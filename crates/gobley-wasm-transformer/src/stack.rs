/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use crate::Transformer;

impl Transformer {
    // Ported from wasm-bindgen
    pub(crate) fn inject_stack_pointer_shim(&mut self) -> anyhow::Result<()> {
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
