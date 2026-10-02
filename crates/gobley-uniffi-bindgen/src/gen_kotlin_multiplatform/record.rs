/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use anyhow::{anyhow, bail, Result};
use uniffi_bindgen::interface::DefaultValue;
use uniffi_bindgen::ComponentInterface;

use super::{CodeType, Config};

#[derive(Debug)]
pub struct RecordCodeType {
    id: String,
}

impl RecordCodeType {
    pub fn new(id: String) -> Self {
        Self { id }
    }
}

impl CodeType for RecordCodeType {
    fn type_label(&self, ci: &ComponentInterface) -> String {
        super::KotlinCodeOracle.class_name(ci, &self.id)
    }

    fn canonical_name(&self) -> String {
        format!("Type{}", self.id)
    }

    fn default(
        &self,
        default: &DefaultValue,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String> {
        let _ = config;
        match default {
            // The generated Kotlin data class only has a no-argument constructor when *every*
            // field has a default. Emitting `RecordName()` for a record with any required field
            // would produce code that fails to compile, so reject a bare `#[uniffi(default)]`
            // here with a clear generation-time error instead of deferring to the Kotlin compiler.
            DefaultValue::Default => {
                let record = ci
                    .get_record_definition(&self.id)
                    .ok_or_else(|| anyhow!("record `{}` is not defined", self.id))?;
                if record
                    .fields()
                    .iter()
                    .all(|field| field.default_value().is_some())
                {
                    Ok(format!("{}()", self.type_label(ci)))
                } else {
                    bail!(
                        "`#[uniffi(default)]` without an explicit value is not supported for \
                         record `{}` because one or more of its fields have no default. Give \
                         every field a default, or provide an explicit default value.",
                        self.type_label(ci)
                    )
                }
            }
            DefaultValue::Literal(_) => bail!(
                "Literals for record types are not supported: {}",
                self.type_label(ci)
            ),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn component_interface(udl: &str) -> ComponentInterface {
        ComponentInterface::from_webidl(udl, "test").expect("valid UDL fixture")
    }

    #[test]
    fn bare_default_allowed_when_all_fields_have_defaults() {
        let ci = component_interface(
            r#"
namespace test {};

dictionary AllDefaults {
    u32 retries = 3;
    boolean verbose = false;
    string? note = null;
};
"#,
        );
        let code_type = RecordCodeType::new("AllDefaults".to_string());
        let rendered = code_type
            .default(&DefaultValue::Default, &ci, &Config::default())
            .expect("all-defaulted record should support a bare default");
        assert_eq!(rendered, "AllDefaults()");
    }

    #[test]
    fn bare_default_rejected_when_a_field_is_required() {
        let ci = component_interface(
            r#"
namespace test {};

dictionary HasRequired {
    string name;
    u32 retries = 3;
};
"#,
        );
        let code_type = RecordCodeType::new("HasRequired".to_string());
        let error = code_type
            .default(&DefaultValue::Default, &ci, &Config::default())
            .expect_err("record with a required field must reject a bare default");
        assert!(
            error.to_string().contains("HasRequired"),
            "error should name the offending record, got: {error}"
        );
    }
}
