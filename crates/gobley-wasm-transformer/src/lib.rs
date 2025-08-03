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
use walrus::{Export, ExportItem, Function, Global, Import, ImportKind, Module, ValType};

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

    fn imports_from_module<'b>(
        &'b self,
        module: impl AsRef<str> + 'b,
    ) -> impl Iterator<Item = &'a Import> + 'b {
        self.module
            .imports
            .iter()
            .filter(move |i| i.module == module.as_ref())
    }

    fn import_to_kt_signature(&self, import: &Import) -> String {
        match import.kind {
            ImportKind::Function(id) => self.function_to_kt_signature(self.module.funcs.get(id)),
            ImportKind::Table(_) => "WebAssembly.Table".to_string(),
            ImportKind::Memory(_) => "WebAssembly.Memory".to_string(),
            ImportKind::Global(id) => Self::global_to_kt_signature(self.module.globals.get(id)),
        }
    }

    fn exports(&self) -> impl Iterator<Item = &Export> {
        self.module.exports.iter()
    }

    fn export_to_kt_signature(&self, export: &Export) -> String {
        match export.item {
            ExportItem::Function(id) => self.function_to_kt_signature(self.module.funcs.get(id)),
            ExportItem::Table(_) => "WebAssembly.Table".to_string(),
            ExportItem::Memory(_) => "WebAssembly.Memory".to_string(),
            ExportItem::Global(id) => Self::global_to_kt_signature(self.module.globals.get(id)),
        }
    }

    fn function_to_kt_signature(&self, function: &Function) -> String {
        let ty = self.module.types.get(function.ty());
        let mut output = String::new();
        let mut first = true;
        output.push('(');

        for param_str in ty.params().iter().map(Self::map_val_type_to_kt) {
            if !first {
                output.push_str(", ");
            }
            first = false;
            output.push_str(param_str);
        }

        output.push_str(") -> ");

        if let Some(result) = ty.results().first() {
            output.push_str(Self::map_val_type_to_kt(result));
        } else {
            output.push_str("Unit");
        }

        output
    }

    fn global_to_kt_signature(global: &Global) -> String {
        let inner_ty = Self::map_val_type_to_kt(&global.ty);
        format!("WebAssembly.Global<{inner_ty}>")
    }

    fn map_val_type_to_kt(ty: &ValType) -> &'static str {
        match ty {
            ValType::I32 => "Int",
            ValType::F32 => "Float",
            ValType::F64 => "Double",
            _ => "Any",
        }
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
