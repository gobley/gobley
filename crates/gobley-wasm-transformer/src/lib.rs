/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use askama::Template;
use base64::Engine;
use walrus::Module;

pub struct Transformer {
    module: Module,
}

#[derive(Template)]
#[template(syntax = "kt", escape = "none", path = "js.kt")]
pub struct KotlinJsRenderer<'a> {
    package_name: Option<&'a str>,
    base64: &'a str,
}

impl Transformer {
    pub fn new(input: &[u8]) -> anyhow::Result<Self> {
        Ok(Self {
            module: Module::from_buffer(input)?,
        })
    }

    pub fn transform(mut self) -> anyhow::Result<Vec<u8>> {
        self.transform_inner()?;
        let Self { mut module } = self;
        Ok(module.emit_wasm())
    }

    pub fn transform_into_base64(self) -> anyhow::Result<String> {
        use base64::prelude::BASE64_STANDARD;
        Ok(BASE64_STANDARD.encode(self.transform()?))
    }

    pub fn transform_into_kt(self, package_name: Option<&str>) -> anyhow::Result<String> {
        let base64 = self.transform_into_base64()?;
        let renderer = KotlinJsRenderer {
            package_name,
            base64: &base64,
        };
        Ok(renderer.render()?)
    }
}

impl Transformer {
    fn transform_inner(&mut self) -> anyhow::Result<()> {
        self.inject_stack_pointer_shim()?;
        Ok(())
    }

    // Ported from wasm-bindgen
    fn inject_stack_pointer_shim(&mut self) -> anyhow::Result<()> {
        use walrus::ir::*;
        use walrus::{FunctionBuilder, ValType};

        let stack_pointer = match self.module.globals.iter().next().map(|g| g.id()) {
            Some(s) => s,
            None => anyhow::bail!("failed to find stack pointer"),
        };

        let mut builder =
            FunctionBuilder::new(&mut self.module.types, &[ValType::I32], &[ValType::I32]);
        builder.name("__gobley_add_to_stack_pointer".to_string());

        let mut body = builder.func_body();
        let arg = self.module.locals.add(ValType::I32);

        body.local_get(arg)
            .global_get(stack_pointer)
            .binop(BinaryOp::I32Add)
            .global_set(stack_pointer)
            .global_get(stack_pointer);

        let add_to_stack_pointer_func = builder.finish(vec![arg], &mut self.module.funcs);

        self.module
            .exports
            .add("__gobley_add_to_stack_pointer", add_to_stack_pointer_func);

        Ok(())
    }
}
