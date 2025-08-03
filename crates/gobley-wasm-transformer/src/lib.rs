/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

pub mod import;
pub mod stack;

use std::collections::BTreeSet;

use askama::Template;
use base64::Engine;
use walrus::{Function, ImportKind, Module, ValType};

use self::import::WasmFunctionImport;

#[derive(Debug)]
pub struct Transformer {
    module: Module,
    function_imports: Vec<WasmFunctionImport>,
}

#[derive(Template)]
#[template(syntax = "kt", escape = "none", path = "js.kt")]
pub struct KotlinJsRenderer<'a> {
    package_name: Option<&'a str>,
    base64: &'a str,
    module: &'a Module,
}

impl<'a> KotlinJsRenderer<'a> {
    fn import_modules(&self) -> Vec<String> {
        let import_modules = self
            .module
            .imports
            .iter()
            .map(|i| &i.module)
            .collect::<BTreeSet<_>>();

        import_modules.iter().map(|i| i.to_string()).collect()
    }

    fn import_functions_from_module<'b>(
        &'b self,
        module: impl AsRef<str> + 'b,
    ) -> impl Iterator<Item = (&'a str, &'a Function)> + 'b {
        self.module
            .imports
            .iter()
            .filter(move |i| i.module == module.as_ref())
            .filter_map(|i| {
                Some((
                    i.name.as_str(),
                    self.module.funcs.get(match i.kind {
                        ImportKind::Function(id) => id,
                        _ => return None,
                    }),
                ))
            })
    }

    fn function_to_kt_signature(&self, function: &Function) -> String {
        let ty = self.module.types.get(function.ty());
        let mut output = String::new();
        let mut first = true;
        output.push('(');

        fn map_val_type_to_kt(ty: &ValType) -> &'static str {
            match ty {
                ValType::I32 => "Int",
                ValType::F32 => "Float",
                ValType::F64 => "Double",
                _ => "Any",
            }
        }

        for param_str in ty.params().iter().map(map_val_type_to_kt) {
            if !first {
                output.push_str(", ");
            }
            first = false;
            output.push_str(param_str);
        }

        output.push_str(") -> ");

        if let Some(result) = ty.results().first() {
            output.push_str(map_val_type_to_kt(result));
        } else {
            output.push_str("Unit");
        }

        output
    }
}

impl Transformer {
    pub fn new(input: &[u8], function_imports: Vec<WasmFunctionImport>) -> anyhow::Result<Self> {
        Ok(Self {
            module: Module::from_buffer(input)?,
            function_imports,
        })
    }

    fn transform(&mut self) -> anyhow::Result<()> {
        self.inject_stack_pointer_shim()?;
        self.inject_function_imports();
        Ok(())
    }

    pub fn render_into_kt(mut self, package_name: Option<&str>) -> anyhow::Result<String> {
        use base64::prelude::BASE64_STANDARD;

        self.transform()?;

        let wasm = self.module.emit_wasm();
        let wasm_base64 = BASE64_STANDARD.encode(wasm);
        let renderer = KotlinJsRenderer {
            package_name,
            base64: &wasm_base64,
            module: &self.module,
        };
        Ok(renderer.render()?)
    }
}
