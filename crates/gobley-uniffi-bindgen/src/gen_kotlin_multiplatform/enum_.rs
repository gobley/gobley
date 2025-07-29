/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use anyhow::{bail, Result};
use uniffi_bindgen::backend::Literal;
use uniffi_bindgen::ComponentInterface;

use super::{CodeType, Config};

#[derive(Debug)]
pub struct EnumCodeType {
    id: String,
}

impl EnumCodeType {
    pub fn new(id: String) -> Self {
        Self { id }
    }
}

impl CodeType for EnumCodeType {
    fn type_label(&self, ci: &ComponentInterface) -> String {
        super::KotlinCodeOracle.class_name(ci, &self.id)
    }

    fn canonical_name(&self) -> String {
        format!("Type{}", self.id)
    }

    fn literal(
        &self,
        literal: &Literal,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String> {
        Ok(match literal {
            Literal::Enum(v, _) => {
                format!(
                    "{}.{}",
                    self.type_label(ci),
                    super::KotlinCodeOracle.enum_variant_name(v, config)
                )
            }
            _ => bail!("Invalid literal for Enum type: {literal:?}"),
        })
    }
}
