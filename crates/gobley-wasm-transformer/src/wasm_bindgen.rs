/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::fmt::Write as _;
use std::io;
use std::mem;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::Context;
use bytes_str::BytesStr;
use swc_atoms::Atom;
use swc_common::comments::Comments;
use swc_common::sync::Lrc;
use swc_common::{FileLoader, FileName, FilePathMapping, Mark, SourceMap};
use swc_common::{Spanned, SyntaxContext};
use swc_ecma_ast::*;
use swc_ecma_codegen::text_writer::JsWriter;
use swc_ecma_codegen::Emitter;
use swc_ecma_parser::{lexer::Lexer, Parser, StringInput, Syntax};
use swc_ecma_transforms_base::helpers::Helpers;
use swc_ecma_transforms_base::resolver;
use swc_ecma_transforms_module::common_js::FeatureFlag;
use swc_ecma_transforms_module::path::Resolver;
use swc_ecma_transforms_module::util::Config;
use swc_ecma_visit::{VisitMut, VisitMutWith as _};

use crate::WasmBindgenJsModules;
use crate::{GlobalEntity, GlobalEntityLang, Transformer};

struct TranspilingResult {
    pub source: String,
}

impl Transformer {
    pub(crate) fn needs_wasm_bindgen(&mut self) -> bool {
        self.module.customs.iter().any(|(_, custom)| {
            // Check the presence of __wasm_bindgen_unstable
            custom.name().starts_with("__wasm_bindgen")
        })
    }

    pub(crate) fn transform_using_wasm_bindgen(&mut self) -> anyhow::Result<()> {
        let mut bindgen = wasm_bindgen_cli_support::Bindgen::new();
        bindgen.bundler(true)?;
        bindgen.input_bytes(Self::WASM_BINDGEN_STEM, self.module.emit_wasm());

        let mut output = bindgen.generate_output()?;

        let inline_snippets = output.snippets().iter().flat_map(|(identifier, list)| {
            list.iter().enumerate().map(move |(i, js)| {
                let filename = format!("./snippets/{identifier}/inline{i}.js");
                (filename, js.as_str())
            })
        });
        let snippets = output.local_modules().iter().map(|(path, js)| {
            let filename = format!("./snippets/{path}");
            (filename, js.as_str())
        });
        let stem_snippet = std::iter::once({
            let filename = Self::WASM_BINDGEN_STEM_FILENAME.to_string();
            let js = output.js();
            (filename, js)
        });

        let mut files = vec![];
        for (filename, js) in inline_snippets.chain(snippets).chain(stem_snippet) {
            self.wasm_bindgen_js_modules.push(WasmBindgenJsModules {
                name: filename.clone(),
            });
            files.push((PathBuf::from(filename), BytesStr::from(js.to_string())));
        }

        let files = files.into();

        let source_map: Lrc<SourceMap> = Lrc::new(SourceMap::with_file_loader(
            Box::new(InMemoryFileLoader {
                files: Arc::clone(&files),
            }),
            FilePathMapping::empty(),
        ));

        for (module_idx, (filename, js)) in files.iter().enumerate() {
            let TranspilingResult { source } =
                Self::transpile_js_module_as_function(&source_map, filename, js)?;

            self.global_entities.push(GlobalEntity {
                modifier: "private".to_string(),
                name: format!("wbgFactory{module_idx}"),
                expr: source,
                ty: "WasmBindgenJsModuleFactory".to_string(),
                lang: GlobalEntityLang::JavaScript,
            });
        }

        self.global_entities.push(GlobalEntity {
            modifier: "private".to_string(),
            name: "wbgFactoryById".to_string(),
            expr: {
                let mut expr = "mapOf(\n".to_string();
                for (module_idx, (filename, _)) in files.iter().enumerate() {
                    writeln!(
                        &mut expr,
                        "    {:?} to `wbgFactory{module_idx}`,",
                        filename.display()
                    )
                    .unwrap();
                }
                expr.push_str(")\n");
                expr
            },
            ty: "Map<String, WasmBindgenJsModuleFactory>".to_string(),
            lang: GlobalEntityLang::Kotlin,
        });

        std::mem::swap(&mut self.module, output.wasm_mut());

        Ok(())
    }

    /// Converts an ES module to a CJS module, written in the ES5 syntax. For example,
    ///
    /// ```js
    /// import * as foo from "foo.js";
    /// import bar1, { bar as bar2 } from "bar.js";
    ///
    /// export function baz() {}
    /// ```
    ///
    /// becomes something like:
    ///
    /// ```js
    /// function (require, module, exports) {
    ///     var foo = require("foo.js");
    ///     var _bar = require("bar.js");
    ///     var bar1 = _bar.default;
    ///     var bar2 = _bar.bar;
    ///     Object.defineProperty(exports, "baz", {get: function () { return baz }});
    ///     function baz() {}
    /// }
    /// ```
    ///
    /// The conversion result can be embedded into the [`js()`] intrinsic function of Kotlin/JS.
    ///
    /// ```kotlin
    /// val __moduleCache: MutableMap<String, dynamic> = mutableMapOf()
    /// val __modules = mapOf(
    ///     "foo.js" to js("""function (require, module, exports) { ... }"""),
    ///     "bar.js" to js("""function (require, module, exports) { ... }"""),
    /// )
    ///
    /// fun require(id: String) {
    ///     val cachedModule = __moduleCache[id]
    ///     if (cachedModule != null) return cachedModule.exports
    ///
    ///     val module = js("""({ exports: {} })""")
    ///     __moduleCache[id] = module
    ///     __modules[id]!!(::require, module, module.exports)
    ///
    ///     return module.exports
    /// }
    /// ```
    ///
    /// [`js()`]: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.js/js.html
    fn transpile_js_module_as_function(
        source_map: &Lrc<SourceMap>,
        filename: &Path,
        js: &BytesStr,
    ) -> anyhow::Result<TranspilingResult> {
        let source_file =
            source_map.new_source_file(FileName::Real(filename.to_path_buf()).into(), js.clone());
        let lexer = Lexer::new(
            Syntax::Es(Default::default()),
            EsVersion::EsNext,
            StringInput::from(&*source_file),
            None,
        );

        // Parse the given file
        let mut parser = Parser::new_from(lexer);
        let module = parser
            .parse_module()
            .map_err(|e| anyhow::Error::msg(format!("{:?} at {:?}", e.kind(), e.span())))
            .with_context(|| format!("parsing {filename:?} failed"))?;

        let module = swc_common::GLOBALS.set(&Default::default(), || {
            swc_ecma_transforms_base::helpers::HELPERS.set(&Helpers::new(false), || {
                use swc_ecma_transforms_compat as compat;

                let unresolved_mark = Mark::new();
                let top_level_mark = Mark::new();

                Program::Module(module)
                    .apply(resolver(unresolved_mark, top_level_mark, false))
                    // Transpile the code into CJS
                    .apply(swc_ecma_transforms_module::common_js(
                        Resolver::Default,
                        unresolved_mark,
                        Config {
                            ..Default::default()
                        },
                        FeatureFlag {
                            support_block_scoping: false,
                            support_arrow: false,
                        },
                    ))
                    // Transpile the code into ES5
                    .apply(compat::es2022(Default::default(), unresolved_mark))
                    .apply(compat::es2021())
                    .apply(compat::es2020(Default::default(), unresolved_mark))
                    .apply(compat::es2019())
                    .apply(compat::es2018(Default::default()))
                    .apply(compat::es2017(Default::default(), unresolved_mark))
                    .apply(compat::es2016())
                    .apply(compat::es2015(
                        unresolved_mark,
                        None::<&dyn Comments>,
                        Default::default(),
                    ))
                    .apply(compat::es3(false))
                    .apply(swc_ecma_visit::visit_mut_pass(LowerObjectAccessors::new()))
                    .apply(swc_ecma_transforms_base::helpers::inject_helpers(
                        Mark::new(),
                    ))
            })
        });

        let mut source_buf = b"function(require, module, exports){".to_vec();
        let writer = JsWriter::new(source_map.clone(), "\n", &mut source_buf, None);
        let mut emitter = Emitter {
            cfg: swc_ecma_codegen::Config::default()
                .with_minify(true)
                .with_target(EsVersion::Es5),
            cm: source_map.clone(),
            comments: None,
            wr: Box::new(writer),
        };

        emitter.emit_program(&module)?;
        source_buf.push(b'}');

        Ok(TranspilingResult {
            source: String::from_utf8(source_buf)
                .with_context(|| "codegen generated non-utf8 output")?,
        })
    }

    pub(crate) const WASM_BINDGEN_STEM: &str = "__gobley_wasm_transformer_wb_stem";
    pub(crate) const WASM_BINDGEN_STEM_FILENAME: &str = "./__gobley_wasm_transformer_wb_stem_bg.js";
}

struct InMemoryFileLoader {
    // Use slice instead of a map to ensure a stable order of files
    files: Arc<[(PathBuf, BytesStr)]>,
}

impl FileLoader for InMemoryFileLoader {
    fn file_exists(&self, path: &Path) -> bool {
        self.files.iter().any(|(p, _)| p == path)
    }

    fn abs_path(&self, _path: &Path) -> Option<PathBuf> {
        None
    }

    fn read_file(&self, path: &Path) -> io::Result<BytesStr> {
        self.files
            .iter()
            .find_map(|(p, f)| (p == path).then_some(f.clone()))
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, format!("not found: {path:?}")))
    }
}

pub struct LowerObjectAccessors {
    counter: u32,
}

impl LowerObjectAccessors {
    pub fn new() -> Self {
        Self { counter: 0 }
    }

    fn next_ident(&mut self) -> Ident {
        self.counter += 1;
        Ident::new(
            Atom::new(format!("__acc_obj_{}", self.counter)),
            swc_common::DUMMY_SP,
            SyntaxContext::empty(),
        )
    }

    /// Converts a object property into a string expression, except for the computed property keys.
    fn property_name_to_key(name: &PropName) -> Expr {
        match name {
            PropName::Ident(i) => Expr::Lit(Lit::Str(Str {
                span: swc_common::DUMMY_SP,
                value: i.sym.clone(),
                raw: None,
            })),
            PropName::Str(s) => Expr::Lit(Lit::Str(s.clone())),
            PropName::Num(n) => Expr::Lit(Lit::Str(Str {
                span: swc_common::DUMMY_SP,
                value: Atom::new(n.value.to_string()),
                raw: None,
            })),
            PropName::Computed(c) => *c.expr.clone(),
            PropName::BigInt(b) => Expr::Lit(Lit::Str(Str {
                span: swc_common::DUMMY_SP,
                value: Atom::new(b.value.to_string()),
                raw: None,
            })),
        }
    }

    /// Extracts getters and setters from the list of object literal properties.
    fn extract_accessors(properties: Vec<PropOrSpread>) -> (Vec<PropOrSpread>, Vec<Prop>) {
        let mut properties_to_preserve: Vec<PropOrSpread> = Vec::with_capacity(properties.len());
        let mut accessors: Vec<Prop> = Vec::new();

        for p in properties {
            match p {
                PropOrSpread::Prop(boxed) => match *boxed {
                    Prop::Getter(_) | Prop::Setter(_) => {
                        accessors.push(*boxed);
                    }
                    other => properties_to_preserve.push(PropOrSpread::Prop(Box::new(other))),
                },
                spread => properties_to_preserve.push(spread),
            }
        }
        (properties_to_preserve, accessors)
    }

    /// ```js
    /// { [function_key]: function(param) { body }, enumerable: true, configurable: true }
    /// ```
    fn build_descriptor(
        function_key: &str,
        param: Option<Pat>,
        body: Option<BlockStmt>,
    ) -> ObjectLit {
        let function = Expr::Fn(FnExpr {
            ident: None,
            function: Box::new(Function {
                params: param
                    .into_iter()
                    .map(|pat| Param {
                        span: swc_common::DUMMY_SP,
                        decorators: vec![],
                        pat,
                    })
                    .collect(),
                decorators: vec![],
                span: swc_common::DUMMY_SP,
                body,
                is_generator: false,
                is_async: false,
                type_params: None,
                return_type: None,
                ctxt: SyntaxContext::empty(),
            }),
        });

        ObjectLit {
            span: swc_common::DUMMY_SP,
            props: vec![
                PropOrSpread::Prop(Box::new(Prop::KeyValue(KeyValueProp {
                    key: PropName::Ident(IdentName::new(function_key.into(), swc_common::DUMMY_SP)),
                    value: Box::new(function),
                }))),
                PropOrSpread::Prop(Box::new(Prop::KeyValue(KeyValueProp {
                    key: PropName::Ident(IdentName::new("enumerable".into(), swc_common::DUMMY_SP)),
                    value: Box::new(Expr::Lit(Lit::Bool(Bool {
                        span: swc_common::DUMMY_SP,
                        value: true,
                    }))),
                }))),
                PropOrSpread::Prop(Box::new(Prop::KeyValue(KeyValueProp {
                    key: PropName::Ident(IdentName::new(
                        "configurable".into(),
                        swc_common::DUMMY_SP,
                    )),
                    value: Box::new(Expr::Lit(Lit::Bool(Bool {
                        span: swc_common::DUMMY_SP,
                        value: true,
                    }))),
                }))),
            ],
        }
    }

    /// ```js
    /// Object.defineProperty(
    ///     obj_ident, key,
    ///     { [function_key]: function(param) { body }, enumerable: true, configurable: true }
    /// )
    /// ```
    fn build_object_define_property_call(
        obj_ident: Ident,
        key: &PropName,
        function_key: &str,
        param: Option<Pat>,
        body: Option<BlockStmt>,
    ) -> CallExpr {
        CallExpr {
            span: swc_common::DUMMY_SP,
            callee: Callee::Expr(Box::new(Expr::Member(MemberExpr {
                span: swc_common::DUMMY_SP,
                obj: Box::new(Expr::Ident(Ident::new(
                    "Object".into(),
                    swc_common::DUMMY_SP,
                    SyntaxContext::empty(),
                ))),
                prop: MemberProp::Ident(IdentName::new(
                    "defineProperty".into(),
                    swc_common::DUMMY_SP,
                )),
            }))),
            args: vec![
                ExprOrSpread {
                    spread: None,
                    expr: Box::new(Expr::Ident(obj_ident)),
                },
                ExprOrSpread {
                    spread: None,
                    expr: Box::new(Self::property_name_to_key(key)),
                },
                ExprOrSpread {
                    spread: None,
                    expr: Box::new(Expr::Object(Self::build_descriptor(
                        function_key,
                        param,
                        body,
                    ))),
                },
            ],
            type_args: None,
            ctxt: SyntaxContext::default(),
        }
    }
}

impl VisitMut for LowerObjectAccessors {
    fn visit_mut_expr(&mut self, expr: &mut Expr) {
        expr.visit_mut_children_with(self);

        let Expr::Object(obj) = expr else { return };
        let (properties_to_preserve, accessors) =
            Self::extract_accessors(mem::take(&mut obj.props));

        if accessors.is_empty() {
            obj.props = properties_to_preserve;
            return;
        }

        let ident = self.next_ident();

        // ```js
        // var <ident> = { preserved properties };
        // ```
        let tmp_decl = Stmt::Decl(Decl::Var(Box::new(VarDecl {
            span: swc_common::DUMMY_SP,
            ctxt: SyntaxContext::empty(),
            kind: VarDeclKind::Var,
            declare: false,
            decls: vec![VarDeclarator {
                span: swc_common::DUMMY_SP,
                name: Pat::Ident(ident.clone().into()),
                init: Some(Box::new(Expr::Object(ObjectLit {
                    span: swc_common::DUMMY_SP,
                    props: properties_to_preserve,
                }))),
                definite: false,
            }],
        })));

        let mut statements: Vec<Stmt> = vec![tmp_decl];

        // ```js
        // Object.defineProperty(
        //     <ident>, key,
        //     { <get or set>: function(param) { body }, enumerable: true, configurable: true }
        // )
        // ```
        for accessor in accessors {
            match accessor {
                Prop::Getter(GetterProp { key, body, .. }) => {
                    statements.push(Stmt::Expr(ExprStmt {
                        span: swc_common::DUMMY_SP,
                        expr: Box::new(Expr::Call(Self::build_object_define_property_call(
                            ident.clone(),
                            &key,
                            "get",
                            None,
                            body,
                        ))),
                    }));
                }

                Prop::Setter(SetterProp {
                    key, param, body, ..
                }) => {
                    statements.push(Stmt::Expr(ExprStmt {
                        span: swc_common::DUMMY_SP,
                        expr: Box::new(Expr::Call(Self::build_object_define_property_call(
                            ident.clone(),
                            &key,
                            "set",
                            Some(Pat::clone(&param)),
                            body,
                        ))),
                    }));
                }

                _ => {}
            }
        }

        // ```js
        // return <ident>;
        // ``````
        statements.push(Stmt::Return(ReturnStmt {
            span: swc_common::DUMMY_SP,
            arg: Some(Box::new(Expr::Ident(ident))),
        }));

        // Replace the original object literal expression with an IIFE.
        *expr = Expr::Call(CallExpr {
            span: swc_common::DUMMY_SP,
            callee: Callee::Expr(Box::new(Expr::Paren(ParenExpr {
                span: swc_common::DUMMY_SP,
                expr: Box::new(Expr::Fn(FnExpr {
                    ident: None,
                    function: Box::new(Function {
                        params: vec![],
                        decorators: vec![],
                        span: swc_common::DUMMY_SP,
                        body: Some(BlockStmt {
                            span: swc_common::DUMMY_SP,
                            stmts: statements,
                            ctxt: SyntaxContext::empty(),
                        }),
                        is_generator: false,
                        is_async: false,
                        type_params: None,
                        return_type: None,
                        ctxt: SyntaxContext::empty(),
                    }),
                })),
            }))),
            args: vec![],
            type_args: None,
            ctxt: SyntaxContext::empty(),
        });
    }
}
