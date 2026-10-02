/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use uniffi_bindgen::{interface::ObjectImpl, ComponentInterface};

use super::CodeType;

#[derive(Debug)]
pub struct ObjectCodeType {
    name: String,
    imp: ObjectImpl,
}

impl ObjectCodeType {
    pub fn new(name: String, imp: ObjectImpl) -> Self {
        Self { name, imp }
    }
}

impl CodeType for ObjectCodeType {
    fn type_label(&self, ci: &ComponentInterface) -> String {
        super::KotlinCodeOracle.class_name(ci, &self.name)
    }

    fn canonical_name(&self) -> String {
        format!("Type{}", self.name)
    }

    fn initialization_fn(&self) -> Option<String> {
        self.imp
            .has_callback_interface()
            .then(|| format!("uniffiCallbackInterface{}.register", self.name))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use uniffi_bindgen::interface::DefaultValue;

    use crate::gen_kotlin_multiplatform::Config;

    #[test]
    fn bare_default_rejected_for_objects() {
        // An object's generated constructor takes an internal handle, so there is no public
        // no-argument constructor to synthesize a default from. A bare `#[uniffi(default)]` must
        // therefore fail at generation time rather than emit uncompilable `Widget()`.
        let ci = ComponentInterface::from_webidl(
            r#"
namespace test {};

interface Widget {
    constructor();
};
"#,
            "test",
        )
        .expect("valid UDL fixture");
        let code_type = ObjectCodeType::new("Widget".to_string(), ObjectImpl::Struct);
        let error = code_type
            .default(&DefaultValue::Default, &ci, &Config::default())
            .expect_err("objects must reject a bare default");
        assert!(
            error.to_string().contains("Widget"),
            "error should name the offending object, got: {error}"
        );
    }
}
