/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use uniffi_bindgen::interface::{ComponentInterface, Variant};

use super::{CodeType, KotlinCodeOracle};

#[derive(Debug)]
pub(super) struct VariantCodeType {
    pub v: Variant,
}

impl CodeType for VariantCodeType {
    fn type_label(&self, ci: &ComponentInterface) -> String {
        KotlinCodeOracle.class_name(ci, self.v.name())
    }

    fn canonical_name(&self) -> String {
        self.v.name().to_string()
    }
}

// impl AsCodeType for Variant
// impl AsCodeType for &Variant
// conflicts with the generic impl of AsCodeType in mod.rs
// because Variant is not a local type (comes from uniffi_bindgen).
// So we inline the implementation in filters::variant_type_name, as a workaround.
