/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::borrow::Borrow;
use std::cell::RefCell;
use std::cmp::Ordering;
use std::collections::{BTreeSet, HashMap, HashSet};
use std::fmt::Debug;

use anyhow::{anyhow, bail, Context, Result};
use askama::Template;
use filters::header_escape_name;
use heck::{ToLowerCamelCase, ToShoutySnakeCase, ToUpperCamelCase};
use serde::{Deserialize, Serialize};
use uniffi_bindgen::interface::*;

mod callback_interface;
mod compounds;
mod custom;
mod enum_;
mod miscellany;
mod object;
mod primitives;
mod record;
mod variant;

#[rustfmt::skip]
const CPP_KEYWORDS: &[&str] = &[
    "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor", "bool",
    "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t", "class",
    "compl", "concept", "const", "const_cast", "consteval", "constexpr", "constinit",
    "continue", "co_await", "co_return", "co_yield", "decltype", "default", "delete",
    "do", "double", "dynamic_cast", "else", "enum", "explicit", "export", "extern",
    "false", "float", "for", "friend", "goto", "if", "inline", "int", "long",
    "mutable", "namespace", "new", "noexcept", "not", "not_eq", "nullptr",
    "operator", "or", "or_eq", "private", "protected", "public", "register",
    "reinterpret_cast", "requires", "return", "short", "signed", "sizeof",
    "static", "static_assert", "static_cast", "struct", "switch", "template",
    "this", "thread_local", "throw", "true", "try", "typedef", "typeid", "typename",
    "union", "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while",
    "xor", "xor_eq"
];

trait CodeType: Debug {
    /// The language specific label used to reference this type. This will be used in
    /// method signatures and property declarations.
    fn type_label(&self, ci: &ComponentInterface) -> String;

    /// A representation of this type label that can be used as part of another
    /// identifier. e.g. `read_foo()`, or `FooInternals`.
    ///
    /// This is especially useful when creating specialized objects or methods to deal
    /// with this type only.
    fn canonical_name(&self) -> String;

    fn literal(
        &self,
        literal: &Literal,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String> {
        let _ = literal;
        let _ = config;
        bail!("Unimplemented for {}", self.type_label(ci))
    }

    /// Name of the FfiConverter
    ///
    /// This is the object that contains the lower, write, lift, and read methods for this type.
    /// Depending on the binding this will either be a singleton or a class with static methods.
    ///
    /// This is the newer way of handling these methods and replaces the lower, write, lift, and
    /// read CodeType methods.  Currently only used by Kotlin, but the plan is to move other
    /// backends to using this.
    fn ffi_converter_name(&self) -> String {
        format!("FfiConverter{}", self.canonical_name())
    }

    /// Function to run at startup
    fn initialization_fn(&self) -> Option<String> {
        None
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub enum ConfigKotlinTarget {
    #[serde(rename = "jvm")]
    Jvm,
    #[serde(rename = "android")]
    Android,
    #[serde(rename = "native")]
    Native,
    #[serde(rename = "stub")]
    Stub,
}

// config options to customize the generated Kotlin.
#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct Config {
    pub(super) package_name: Option<String>,
    pub(super) cdylib_name: Option<String>,
    #[serde(default)]
    pub(super) kotlin_multiplatform: bool,
    #[serde(default)]
    kotlin_targets: Vec<ConfigKotlinTarget>,
    generate_immutable_records: Option<bool>,
    #[serde(default)]
    omit_checksums: bool,
    #[serde(default)]
    custom_types: HashMap<String, CustomTypeConfig>,
    #[serde(default)]
    pub(super) external_packages: HashMap<String, String>,
    #[serde(default)]
    kotlin_target_version: Option<String>,
    #[serde(default)]
    disable_java_cleaner: bool,
    generate_serializable_types: Option<bool>,
    #[serde(default)]
    use_pascal_case_enum_class: Option<bool>,
    #[serde(default)]
    jvm_dynamic_library_dependencies: Vec<String>,
    #[serde(default)]
    android_dynamic_library_dependencies: Vec<String>,
    #[serde(default)]
    dynamic_library_dependencies: Vec<String>,
}

// TODO: Make this public in 0.4.0
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
pub(crate) enum Visibility {
    #[serde(rename = "public")]
    Public,
    #[serde(rename = "internal")]
    Internal,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct CustomTypeConfig {
    imports: Option<Vec<String>>,
    type_name: Option<String>,
    into_custom: String, // b/w compat alias for lift
    lift: String,
    from_custom: String, // b/w compat alias for lower
    lower: String,
}

// functions replace literal "{}" in strings with a specified value.
impl CustomTypeConfig {
    fn lift(&self, name: &str) -> String {
        let converter = if self.lift.is_empty() {
            &self.into_custom
        } else {
            &self.lift
        };
        converter.replace("{}", name)
    }

    fn lower(&self, name: &str) -> String {
        let converter = if self.lower.is_empty() {
            &self.from_custom
        } else {
            &self.lower
        };
        converter.replace("{}", name)
    }
}

impl Config {
    // We insist someone has already configured us - any defaults we supply would be wrong.
    pub fn package_name(&self) -> String {
        self.package_name
            .as_ref()
            .expect("package name should have been set in update_component_configs")
            .clone()
    }

    pub fn cdylib_name(&self) -> String {
        self.cdylib_name
            .as_ref()
            .expect("cdylib name should have been set in update_component_configs")
            .clone()
    }

    /// Whether to generate immutable records (`val` instead of `var`)
    pub fn generate_immutable_records(&self) -> bool {
        self.generate_immutable_records.unwrap_or(false)
    }

    fn kotlin_version_is_at_least(&self, major: usize, minor: usize, patch: usize) -> bool {
        let Some(kotlin_target_version) = &self.kotlin_target_version else {
            return false;
        };
        let mut kotlin_target_version = kotlin_target_version.split(|c: char| !c.is_numeric());

        for required_version in [major, minor, patch] {
            let Some(current_version) = kotlin_target_version
                .next()
                .and_then(|v| v.parse::<usize>().ok())
            else {
                return required_version == 0;
            };
            match required_version.cmp(&current_version) {
                Ordering::Equal => continue,
                Ordering::Greater => return false,
                Ordering::Less => return true,
            }
        }

        true
    }

    pub fn use_enum_entries(&self) -> bool {
        // Enum.entries became stable in Kotlin 1.9.0 (introduced in 1.8.20)
        self.kotlin_version_is_at_least(1, 9, 0)
    }

    pub fn use_data_objects(&self) -> bool {
        // data objects became stable in Kotlin 1.9.0 (introduced in 1.8.20)
        self.kotlin_version_is_at_least(1, 9, 0)
    }

    pub fn generate_serializable(&self) -> bool {
        self.generate_serializable_types.unwrap_or(false)
    }

    pub fn jvm_dynamic_library_dependencies(&self) -> Vec<String> {
        let mut libraries = self.jvm_dynamic_library_dependencies.clone();
        libraries.extend_from_slice(&self.dynamic_library_dependencies);
        libraries
    }

    pub fn android_dynamic_library_dependencies(&self) -> Vec<String> {
        let mut libraries = self.android_dynamic_library_dependencies.clone();
        libraries.extend_from_slice(&self.dynamic_library_dependencies);
        libraries
    }

    pub fn dynamic_library_dependencies(&self, module_name: &str) -> Vec<String> {
        match module_name {
            "jvm" => self.jvm_dynamic_library_dependencies(),
            "android" => self.android_dynamic_library_dependencies(),
            _ => vec![],
        }
    }

    // Get the package name for an external type
    pub fn external_package_name(&self, module_path: &str, namespace: Option<&str>) -> String {
        // config overrides are keyed by the crate name, default fallback is the namespace.
        let crate_name = module_path.split("::").next().unwrap();
        match self.external_packages.get(crate_name) {
            Some(name) => name.clone(),
            // If the module path is not in `external_packages`, we need to fall back to a default
            // with the namespace, which we hopefully have.  This is quite fragile, but it's
            // unreachable in library mode - all deps get an entry in `external_packages` with the
            // correct namespace.
            None => format!("uniffi.{}", namespace.unwrap_or(module_path)),
        }
    }
}

pub struct MultiplatformBindings {
    pub common: String,
    pub jvm: Option<String>,
    pub android: Option<String>,
    pub native: Option<String>,
    pub stub: Option<String>,
    pub header: Option<String>,
}

// Generate kotlin bindings for the given ComponentInterface, as a string.
pub fn generate_bindings(
    config: &Config,
    ci: &ComponentInterface,
) -> Result<MultiplatformBindings> {
    let common = CommonKotlinWrapper::new("common", Some(Visibility::Public), config.clone(), ci)
        .context("failed to create a common binding generator")?
        .render()
        .context("failed to render common Kotlin bindings")?;

    fn run_with_target(
        config: &Config,
        target: ConfigKotlinTarget,
        f: impl FnOnce() -> Result<String>,
    ) -> Result<Option<String>> {
        config
            .kotlin_targets
            .contains(&target)
            .then(f)
            .map_or(Ok(None), |v| v.map(Some))
    }

    let jvm = run_with_target(config, ConfigKotlinTarget::Jvm, || {
        AndroidJvmKotlinWrapper::new("jvm", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a JVM binding generator")?
            .render()
            .context("failed to render Kotlin/JVM bindings")
    })?;

    let android = run_with_target(config, ConfigKotlinTarget::Android, || {
        AndroidJvmKotlinWrapper::new("android", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a Android binding generator")?
            .render()
            .context("failed to render Android Kotlin/JVM bindings")
    })?;

    let native = run_with_target(config, ConfigKotlinTarget::Native, || {
        NativeKotlinWrapper::new("native", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a native binding generator")?
            .render()
            .context("failed to render Kotlin/Native bindings")
    })?;

    let stub = run_with_target(config, ConfigKotlinTarget::Stub, || {
        StubKotlinWrapper::new("stub", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a stub binding generator")?
            .render()
            .context("failed to render stub bindings")
    })?;

    let header = run_with_target(config, ConfigKotlinTarget::Native, || {
        HeadersKotlinWrapper::new("headers", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a native header binding generator")?
            .render()
            .context("failed to render Kotlin/Native headers")
    })?;

    Ok(MultiplatformBindings {
        common,
        jvm,
        android,
        native,
        stub,
        header,
    })
}

/// A struct to record a Kotlin import statement.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum ImportRequirement {
    /// The name we are importing.
    Import { name: String },
    /// Import the name with the specified local name.
    ImportAs { name: String, as_name: String },
}

impl ImportRequirement {
    /// Render the Kotlin import statement.
    fn render(&self) -> String {
        match &self {
            ImportRequirement::Import { name } => format!("import {name}"),
            ImportRequirement::ImportAs { name, as_name } => {
                format!("import {name} as {as_name}")
            }
        }
    }
}

macro_rules! kotlin_type_renderer {
    ($TypeRenderer:ident, $source_file:literal) => {
        /// Renders Kotlin helper code for all types
        ///
        /// This template is a bit different than others in that it stores internal state from the render
        /// process.  Make sure to only call `render()` once.
        #[derive(Template)]
        #[template(syntax = "kt", escape = "none", path = $source_file)]
        #[allow(dead_code)]
        pub struct $TypeRenderer<'a> {
            module_name: &'a str,
            visibility: Option<Visibility>,
            config: &'a Config,
            ci: &'a ComponentInterface,
            // Track imports added with the `add_import()` macro
            imports: RefCell<BTreeSet<ImportRequirement>>,
        }

        #[allow(dead_code)]
        impl<'a> $TypeRenderer<'a> {
            fn new(
                module_name: &'a str,
                visibility: Option<Visibility>,
                config: &'a Config,
                ci: &'a ComponentInterface,
            ) -> Self {
                Self {
                    module_name,
                    visibility,
                    config,
                    ci,
                    imports: RefCell::new(BTreeSet::new()),
                }
            }

            // Get the package name for an external type
            fn external_type_package_name(&self, module_path: &str, namespace: &str) -> String {
                self.config
                    .external_package_name(module_path, Some(namespace))
            }

            // The following methods are used by the `Types.kt` macros.

            // Helper to add an import statement
            //
            // Call this inside your template to cause an import statement to be added at the top of the
            // file.  Imports will be sorted and de-deuped.
            //
            // Returns an empty string so that it can be used inside an askama `{{ }}` block.
            fn add_import(&self, name: &str) -> &str {
                self.imports.borrow_mut().insert(ImportRequirement::Import {
                    name: name.to_owned(),
                });
                ""
            }

            // Like add_import, but arranges for `import name as as_name`
            fn add_import_as(&self, name: &str, as_name: &str) -> &str {
                self.imports
                    .borrow_mut()
                    .insert(ImportRequirement::ImportAs {
                        name: name.to_owned(),
                        as_name: as_name.to_owned(),
                    });
                ""
            }

            fn visibility(&self) -> &str {
                match self.visibility {
                    None => "",
                    Some(Visibility::Public) => "public ",
                    Some(Visibility::Internal) => "internal ",
                }
            }
        }
    };
}

macro_rules! kotlin_wrapper {
    ($KotlinWrapper:ident, $TypeRenderer:ident, $source_file:literal) => {
        #[derive(Template)]
        #[template(syntax = "kt", escape = "none", path = $source_file)]
        #[allow(dead_code)]
        pub struct $KotlinWrapper<'a> {
            module_name: &'a str,
            visibility: Option<Visibility>,
            config: Config,
            ci: &'a ComponentInterface,
            type_helper_code: String,
            type_imports: BTreeSet<ImportRequirement>,
        }

        #[allow(dead_code)]
        impl<'a> $KotlinWrapper<'a> {
            pub fn new(
                module_name: &'a str,
                visibility: Option<Visibility>,
                config: Config,
                ci: &'a ComponentInterface,
            ) -> Result<Self> {
                let type_renderer = $TypeRenderer::new(module_name, visibility, &config, ci);
                let type_helper_code = type_renderer.render()?;
                let type_imports = type_renderer.imports.into_inner();
                Ok(Self {
                    module_name,
                    visibility,
                    config,
                    ci,
                    type_helper_code,
                    type_imports,
                })
            }

            fn initialization_fns(&self, ci: &ComponentInterface) -> Vec<String> {
                let init_fns = self
                    .ci
                    .iter_local_types()
                    .map(|t| KotlinCodeOracle.find(t))
                    .filter_map(|ct| ct.initialization_fn())
                    .map(|fn_name| format!("{fn_name}(lib)"));

                // Also call global initialization function for any external type we use.
                // For example, we need to make sure that all callback interface vtables are registered
                // (#2343).
                let extern_module_init_fns = self
                    .ci
                    .iter_external_types()
                    .filter_map(|ty| ty.module_path())
                    .map(|module_path| {
                        let namespace = ci.namespace_for_module_path(module_path).unwrap();
                        let package_name = self
                            .config
                            .external_package_name(module_path, Some(namespace));
                        format!("{package_name}.uniffiEnsureInitialized()")
                    })
                    .collect::<HashSet<_>>();

                init_fns.chain(extern_module_init_fns).collect()
            }

            fn imports(&self) -> Vec<ImportRequirement> {
                self.type_imports.iter().cloned().collect()
            }

            fn visibility(&self) -> &str {
                match self.visibility {
                    None => "",
                    Some(Visibility::Public) => "public ",
                    Some(Visibility::Internal) => "internal ",
                }
            }
        }
    };
}

kotlin_type_renderer!(CommonTypeRenderer, "common/Types.kt");
kotlin_wrapper!(CommonKotlinWrapper, CommonTypeRenderer, "common/wrapper.kt");

kotlin_type_renderer!(AndroidJvmTypeRenderer, "android+jvm/Types.kt");
kotlin_wrapper!(
    AndroidJvmKotlinWrapper,
    AndroidJvmTypeRenderer,
    "android+jvm/wrapper.kt"
);

kotlin_type_renderer!(NativeTypeRenderer, "native/Types.kt");
kotlin_wrapper!(NativeKotlinWrapper, NativeTypeRenderer, "native/wrapper.kt");

kotlin_type_renderer!(StubTypeRenderer, "stub/Types.kt");
kotlin_wrapper!(StubKotlinWrapper, StubTypeRenderer, "stub/wrapper.kt");

kotlin_type_renderer!(HeadersTypeRenderer, "headers/Types.h");
kotlin_wrapper!(
    HeadersKotlinWrapper,
    HeadersTypeRenderer,
    "headers/wrapper.h"
);

/// Get the name of the interface and class name for a trait.
///
/// For a regular `struct Foo` or `trait Foo`, there's `FooInterface` with `Foo` as
/// the name of the (Rust implemented) object. But if it's a foreign trait:
/// * The name `Foo` is the name of the interface used by a the Kotlin implementation of the trait.
/// * The Rust implemented object is `FooImpl`.
///
/// This all impacts what types `FfiConverter.lower()` inputs.  If it's a "foreign trait"
/// `lower` must lower anything that implements the interface (ie, a kotlin implementation).
/// If not, then lower only lowers the concrete class (ie, our simple instance with the pointer).
fn object_interface_name(ci: &ComponentInterface, obj: &Object) -> String {
    let class_name = KotlinCodeOracle.class_name(ci, obj.name());
    if obj.has_callback_interface() {
        class_name
    } else {
        format!("{class_name}Interface")
    }
}

// *sigh* - same thing for a trait, which might be either Object or CallbackInterface.
// (we should either fold it into object or kill it!)
fn trait_interface_name(ci: &ComponentInterface, name: &str) -> Result<String> {
    let (obj_name, has_callback_interface) = match ci.get_object_definition(name) {
        Some(obj) => (obj.name(), obj.has_callback_interface()),
        None => (
            ci.get_callback_interface_definition(name)
                .ok_or_else(|| anyhow!("no interface {}", name))?
                .name(),
            true,
        ),
    };
    let class_name = KotlinCodeOracle.class_name(ci, obj_name);
    if has_callback_interface {
        Ok(class_name)
    } else {
        Ok(format!("{class_name}Interface"))
    }
}

// The name of the object exposing a Rust implementation.
fn object_impl_name(ci: &ComponentInterface, obj: &Object) -> String {
    let class_name = KotlinCodeOracle.class_name(ci, obj.name());
    if obj.has_callback_interface() {
        format!("{class_name}Impl")
    } else {
        class_name
    }
}

#[derive(Clone)]
pub struct KotlinCodeOracle;

impl KotlinCodeOracle {
    fn find(&self, type_: &Type) -> Box<dyn CodeType> {
        type_.clone().as_type().as_codetype()
    }

    /// Get the idiomatic Kotlin rendering of a class name (for enums, records, errors, etc).
    fn class_name(&self, ci: &ComponentInterface, nm: &str) -> String {
        let name = nm.to_string().to_upper_camel_case();
        // fixup errors.
        ci.is_name_used_as_error(nm)
            .then(|| self.convert_error_suffix(&name))
            .unwrap_or(name)
    }

    fn convert_error_suffix(&self, nm: &str) -> String {
        match nm.strip_suffix("Error") {
            None => nm.to_string(),
            Some(stripped) => format!("{stripped}Exception"),
        }
    }

    /// Get the idiomatic Kotlin rendering of a function name.
    fn fn_name(&self, nm: &str) -> String {
        format!("`{}`", nm.to_string().to_lower_camel_case())
    }

    /// Get the idiomatic Kotlin rendering of a variable name.
    fn var_name(&self, nm: &str) -> String {
        format!("`{}`", self.var_name_raw(nm))
    }

    /// `var_name` without the backticks.  Useful for using in `@Structure.FieldOrder`.
    pub fn var_name_raw(&self, nm: &str) -> String {
        header_escape_name(&nm.to_lower_camel_case()).unwrap()
    }

    /// Get the idiomatic Kotlin rendering of an individual enum variant.
    fn enum_variant_name(&self, nm: &str, config: &Config) -> String {
        if config.use_pascal_case_enum_class == Some(true) {
            nm.to_upper_camel_case()
        } else {
            nm.to_shouty_snake_case()
        }
    }

    /// Get the idiomatic Kotlin rendering of an FFI callback function name
    fn ffi_callback_name(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    fn ffi_callback_name_header(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    /// Get the idiomatic Kotlin rendering of an FFI struct name
    fn ffi_struct_name(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    fn ffi_struct_name_header(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    fn ffi_type_label_by_value(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            FfiType::RustBuffer(_) => format!("{}ByValue", self.ffi_type_label(ffi_type, ci)),
            FfiType::Struct(name) => format!("{}UniffiByValue", self.ffi_struct_name(name)),
            FfiType::Callback(name) => self.ffi_callback_name(name),
            _ => self.ffi_type_label(ffi_type, ci),
        }
    }

    /// FFI type name to use inside structs
    ///
    /// The main requirement here is that all types must have default values or else the struct
    /// won't work in some JNA contexts.
    fn ffi_type_label_for_ffi_struct(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            // Make callbacks function pointers nullable. This matches the semantics of a C
            // function pointer better and allows for `null` as a default value.
            // NOTE: Type any used here, as native and jvm types differ.
            FfiType::Callback(name) => format!("{}?", self.ffi_callback_name(name)),
            _ => self.ffi_type_label_by_value(ffi_type, ci),
        }
    }

    /// Default values for FFI
    ///
    /// This is used to:
    ///   - Set a default return value for error results
    ///   - Set a default for structs, which JNA sometimes requires
    fn ffi_default_value(&self, ffi_type: &FfiType) -> String {
        match ffi_type {
            FfiType::UInt8 | FfiType::Int8 => "0.toByte()".to_owned(),
            FfiType::UInt16 | FfiType::Int16 => "0.toShort()".to_owned(),
            FfiType::UInt32 | FfiType::Int32 => "0".to_owned(),
            FfiType::UInt64 | FfiType::Int64 => "0.toLong()".to_owned(),
            FfiType::Float32 => "0.0f".to_owned(),
            FfiType::Float64 => "0.0".to_owned(),
            // NOTE: NullPointer is the same as Pointer.NULL
            FfiType::RustArcPtr(_) => "NullPointer".to_owned(),
            FfiType::RustBuffer(_) => "RustBufferHelper.allocValue()".to_owned(),
            FfiType::Callback(_) => "null".to_owned(),
            FfiType::RustCallStatus => "UniffiRustCallStatusHelper.allocValue()".to_owned(),
            _ => unimplemented!("ffi_default_value: {ffi_type:?}"),
        }
    }

    fn ffi_type_label_by_reference(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            FfiType::Int8
            | FfiType::UInt8
            | FfiType::Int16
            | FfiType::UInt16
            | FfiType::Int32
            | FfiType::UInt32
            | FfiType::Int64
            | FfiType::UInt64
            | FfiType::Float32
            | FfiType::Float64 => format!("{}ByReference", self.ffi_type_label(ffi_type, ci)),
            FfiType::RustArcPtr(_) => "PointerByReference".to_owned(),
            // JNA structs default to ByReference
            FfiType::RustBuffer(_) | FfiType::Struct(_) => self.ffi_type_label(ffi_type, ci),
            _ => panic!("{ffi_type:?} by reference is not implemented"),
        }
    }

    fn ffi_type_label_by_mut_reference(
        &self,
        ffi_type: &FfiType,
        ci: &ComponentInterface,
    ) -> String {
        match ffi_type {
            FfiType::Int8
            | FfiType::UInt8
            | FfiType::Int16
            | FfiType::UInt16
            | FfiType::Int32
            | FfiType::UInt32
            | FfiType::Int64
            | FfiType::UInt64
            | FfiType::Float32
            | FfiType::Float64 => format!("{}ByReference", self.ffi_type_label(ffi_type, ci)),
            FfiType::RustArcPtr(_) => "PointerByReference".to_owned(),
            // JNA structs default to ByReference
            FfiType::RustBuffer(_) | FfiType::Struct(_) => self.ffi_type_label(ffi_type, ci),
            _ => panic!("{ffi_type:?} by reference is not implemented"),
        }
    }

    fn ffi_type_label_by_reference_header(
        &self,
        ffi_type: &FfiType,
        ci: &ComponentInterface,
    ) -> String {
        match ffi_type {
            FfiType::Int8
            | FfiType::UInt8
            | FfiType::Int16
            | FfiType::UInt16
            | FfiType::Int32
            | FfiType::UInt32
            | FfiType::Int64
            | FfiType::UInt64
            | FfiType::Float32
            | FfiType::Float64 => format!("{} const *", self.ffi_type_label_header(ffi_type, ci)),
            FfiType::RustArcPtr(_) => "void * const *".to_owned(),
            // JNA structs default to ByReference
            FfiType::RustBuffer(_) | FfiType::Struct(_) => {
                format!("{} const *", self.ffi_type_label_header(ffi_type, ci))
            }
            _ => panic!("{ffi_type:?} by reference is not implemented"),
        }
    }

    fn ffi_type_label_by_mut_reference_header(
        &self,
        ffi_type: &FfiType,
        ci: &ComponentInterface,
    ) -> String {
        match ffi_type {
            FfiType::Int8
            | FfiType::UInt8
            | FfiType::Int16
            | FfiType::UInt16
            | FfiType::Int32
            | FfiType::UInt32
            | FfiType::Int64
            | FfiType::UInt64
            | FfiType::Float32
            | FfiType::Float64 => format!("{} *", self.ffi_type_label_header(ffi_type, ci)),
            FfiType::RustArcPtr(_) => "void **".to_owned(),
            // JNA structs default to ByReference
            FfiType::RustBuffer(_) | FfiType::Struct(_) => {
                format!("{} *", self.ffi_type_label_header(ffi_type, ci))
            }
            _ => panic!("{ffi_type:?} by reference is not implemented"),
        }
    }

    fn ffi_type_label(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            // Note that unsigned integers in Kotlin are currently experimental, but java.nio.ByteBuffer does not
            // support them yet. Thus, we use the signed variants to represent both signed and unsigned
            // types from the component API.
            FfiType::Int8 | FfiType::UInt8 => "Byte".to_string(),
            FfiType::Int16 | FfiType::UInt16 => "Short".to_string(),
            FfiType::Int32 | FfiType::UInt32 => "Int".to_string(),
            FfiType::Int64 | FfiType::UInt64 => "Long".to_string(),
            FfiType::Float32 => "Float".to_string(),
            FfiType::Float64 => "Double".to_string(),
            FfiType::Handle => "Long".to_string(),
            FfiType::RustArcPtr(_) => "Pointer?".to_string(),
            FfiType::RustBuffer(maybe_external) => match maybe_external {
                Some(external_meta) if external_meta.module_path != ci.crate_name() => {
                    format!("RustBuffer{}", external_meta.name)
                }
                _ => "RustBuffer".to_string(),
            },
            FfiType::RustCallStatus => "UniffiRustCallStatusByValue".to_string(),
            FfiType::ForeignBytes => "ForeignBytesByValue".to_string(),
            FfiType::Callback(callback) => self.ffi_callback_name(callback),
            FfiType::Struct(name) => self.ffi_struct_name(name),
            FfiType::Reference(inner) => self.ffi_type_label_by_reference(inner, ci),
            FfiType::MutReference(inner) => self.ffi_type_label_by_mut_reference(inner, ci),
            FfiType::VoidPointer => "Pointer".to_string(),
        }
    }

    fn ffi_type_label_header(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            // Note that unsigned integers in Kotlin are currently experimental, but java.nio.ByteBuffer does not
            // support them yet. Thus, we use the signed variants to represent both signed and unsigned
            // types from the component API.
            FfiType::Int8 | FfiType::UInt8 => "int8_t".to_string(),
            FfiType::Int16 | FfiType::UInt16 => "int16_t".to_string(),
            FfiType::Int32 | FfiType::UInt32 => "int32_t".to_string(),
            FfiType::Int64 | FfiType::UInt64 => "int64_t".to_string(),
            FfiType::Float32 => "float".to_string(),
            FfiType::Float64 => "double".to_string(),
            FfiType::Handle => "int64_t".to_string(),
            FfiType::RustArcPtr(_) => "void *".to_string(),
            FfiType::RustBuffer(maybe_external) => match maybe_external {
                Some(external_meta) if external_meta.module_path != ci.crate_name() => {
                    format!("RustBuffer{}", external_meta.name)
                }
                _ => "RustBuffer".to_string(),
            },
            FfiType::RustCallStatus => "UniffiRustCallStatus".to_string(),
            FfiType::ForeignBytes => "ForeignBytes".to_string(),
            FfiType::Callback(name) => self.ffi_callback_name_header(name),
            FfiType::Struct(name) => self.ffi_struct_name_header(name),
            FfiType::Reference(inner) => self.ffi_type_label_by_reference_header(inner, ci),
            FfiType::MutReference(inner) => self.ffi_type_label_by_mut_reference_header(inner, ci),
            FfiType::VoidPointer => "void *".to_string(),
        }
    }

    #[allow(clippy::only_used_in_recursion)]
    fn as_external_type(&self, ci: &ComponentInterface, as_ct: &impl AsType) -> Option<String> {
        let ty = as_ct.as_type();
        if let (true, Some(name)) = (ci.is_external(&ty), ty.name()) {
            return Some(name.to_string());
        }
        match as_ct.as_type() {
            Type::Custom { builtin, .. } => self.as_external_type(ci, &builtin),
            _ => None,
        }
    }
}

trait AsCodeType {
    fn as_codetype(&self) -> Box<dyn CodeType>;
}

impl<T: AsType> AsCodeType for T {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        // Map `Type` instances to a `Box<dyn CodeType>` for that type.
        //
        // There is a companion match in `templates/Types.kt` which performs a similar function for the
        // template code.
        //
        //   - When adding additional types here, make sure to also add a match arm to the `Types.kt` template.
        //   - To keep things manageable, let's try to limit ourselves to these 2 mega-matches
        match self.as_type() {
            Type::UInt8 => Box::new(primitives::UInt8CodeType),
            Type::Int8 => Box::new(primitives::Int8CodeType),
            Type::UInt16 => Box::new(primitives::UInt16CodeType),
            Type::Int16 => Box::new(primitives::Int16CodeType),
            Type::UInt32 => Box::new(primitives::UInt32CodeType),
            Type::Int32 => Box::new(primitives::Int32CodeType),
            Type::UInt64 => Box::new(primitives::UInt64CodeType),
            Type::Int64 => Box::new(primitives::Int64CodeType),
            Type::Float32 => Box::new(primitives::Float32CodeType),
            Type::Float64 => Box::new(primitives::Float64CodeType),
            Type::Boolean => Box::new(primitives::BooleanCodeType),
            Type::String => Box::new(primitives::StringCodeType),
            Type::Bytes => Box::new(primitives::BytesCodeType),

            Type::Timestamp => Box::new(miscellany::TimestampCodeType),
            Type::Duration => Box::new(miscellany::DurationCodeType),

            Type::Enum { name, .. } => Box::new(enum_::EnumCodeType::new(name)),
            Type::Object { name, imp, .. } => Box::new(object::ObjectCodeType::new(name, imp)),
            Type::Record { name, .. } => Box::new(record::RecordCodeType::new(name)),
            Type::CallbackInterface { name, .. } => {
                Box::new(callback_interface::CallbackInterfaceCodeType::new(name))
            }
            Type::Optional { inner_type } => {
                Box::new(compounds::OptionalCodeType::new(*inner_type))
            }
            Type::Sequence { inner_type } => {
                Box::new(compounds::SequenceCodeType::new(*inner_type))
            }
            Type::Map {
                key_type,
                value_type,
            } => Box::new(compounds::MapCodeType::new(*key_type, *value_type)),
            Type::Custom { name, .. } => Box::new(custom::CustomCodeType::new(name)),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DataClassFieldType {
    Bytes,
    NullableBytes,
    NonNullableNonBytes,
    NullableNonBytes,
}

mod filters {
    pub use uniffi_bindgen::backend::filters::*;
    use uniffi_bindgen::{backend::filters::to_askama_error, interface::ffi::ExternalFfiMetadata};
    use uniffi_meta::LiteralMetadata;
    use variant::VariantCodeType;

    use super::*;

    pub(super) fn type_name(
        as_ct: &impl AsCodeType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().type_label(ci))
    }

    // Workaround problem with impl AsCodeType for &Variant (see variant.rs).
    pub fn variant_type_name(
        v: &Variant,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(VariantCodeType { v: v.clone() }.type_label(ci))
    }

    pub(super) fn canonical_name(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().canonical_name())
    }

    pub(super) fn ffi_converter_name(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().ffi_converter_name())
    }

    pub(super) fn lower_fn(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(format!(
            "{}.lower",
            as_ct.as_codetype().ffi_converter_name()
        ))
    }

    pub(super) fn allocation_size_fn(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(format!(
            "{}.allocationSize",
            as_ct.as_codetype().ffi_converter_name()
        ))
    }

    pub(super) fn write_fn(
        as_ct: &impl AsType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        if let Some(external_type_name) = KotlinCodeOracle.as_external_type(ci, as_ct) {
            Ok(format!(
                "{}.write{external_type_name}",
                as_ct.as_codetype().ffi_converter_name()
            ))
        } else {
            Ok(format!(
                "{}.write",
                as_ct.as_codetype().ffi_converter_name()
            ))
        }
    }

    pub(super) fn lift_fn(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(format!("{}.lift", as_ct.as_codetype().ffi_converter_name()))
    }

    pub(super) fn as_ffi_type(as_ct: &impl AsType) -> Result<FfiType, askama::Error> {
        Ok(FfiType::from(as_ct.as_type()))
    }

    pub(super) fn need_non_null_assertion(type_: &FfiType) -> Result<bool, askama::Error> {
        Ok(matches!(type_, FfiType::RustArcPtr(_)))
    }

    pub(super) fn read_fn(
        as_ct: &impl AsType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        if let Some(external_type_name) = KotlinCodeOracle.as_external_type(ci, as_ct) {
            Ok(format!(
                "{}.read{external_type_name}",
                as_ct.as_codetype().ffi_converter_name()
            ))
        } else {
            Ok(format!("{}.read", as_ct.as_codetype().ffi_converter_name()))
        }
    }

    pub fn render_literal(
        literal: &Literal,
        as_ct: &impl AsType,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error> {
        as_ct
            .as_codetype()
            .literal(literal, ci, config)
            .map_err(|e| to_askama_error(&e))
    }

    // Get the idiomatic Kotlin rendering of an integer.
    fn int_literal(t: &Option<Type>, base10: String) -> Result<String, askama::Error> {
        if let Some(t) = t {
            match t {
                Type::Int8 | Type::Int16 | Type::Int32 | Type::Int64 => Ok(base10),
                Type::UInt8 | Type::UInt16 | Type::UInt32 | Type::UInt64 => Ok(base10 + "u"),
                _ => Err(to_askama_error("Only ints are supported.")),
            }
        } else {
            Err(to_askama_error("Enum hasn't defined a repr"))
        }
    }

    // Get the idiomatic Kotlin rendering of an individual enum variant's discriminant
    pub fn variant_discr_literal(e: &Enum, index: &usize) -> Result<String, askama::Error> {
        let literal = e.variant_discr(*index).expect("invalid index");
        match literal {
            // Kotlin doesn't convert between signed and unsigned by default
            // so we'll need to make sure we define the type as appropriately
            LiteralMetadata::UInt(v, _, _) => int_literal(e.variant_discr_type(), v.to_string()),
            LiteralMetadata::Int(v, _, _) => int_literal(e.variant_discr_type(), v.to_string()),
            _ => Err(to_askama_error("Only ints are supported.")),
        }
    }

    pub fn should_generate_equals_hash_code_record(record: &Record) -> Result<bool, askama::Error> {
        Ok(record.fields().iter().any(|f| {
            matches!(
                as_data_class_field_type(f),
                Ok(DataClassFieldType::Bytes | DataClassFieldType::NullableBytes)
            )
        }))
    }

    pub fn should_generate_equals_hash_code_enum_variant(
        variant: &Variant,
    ) -> Result<bool, askama::Error> {
        Ok(variant.fields().iter().any(|f| {
            matches!(
                as_data_class_field_type(f),
                Ok(DataClassFieldType::Bytes | DataClassFieldType::NullableBytes)
            )
        }))
    }

    pub fn as_data_class_field_type(
        as_ct: &impl AsType,
    ) -> Result<DataClassFieldType, askama::Error> {
        fn as_bytes_field_type_inner(type_: &Type) -> DataClassFieldType {
            match type_ {
                Type::Bytes => DataClassFieldType::Bytes,
                Type::Optional { inner_type } => match as_bytes_field_type_inner(inner_type) {
                    DataClassFieldType::Bytes | DataClassFieldType::NullableBytes => {
                        DataClassFieldType::NullableBytes
                    }
                    DataClassFieldType::NonNullableNonBytes
                    | DataClassFieldType::NullableNonBytes => DataClassFieldType::NullableNonBytes,
                },
                _ => DataClassFieldType::NonNullableNonBytes,
            }
        }
        Ok(as_bytes_field_type_inner(&as_ct.as_type()))
    }

    fn serializable_type(type_: &Type, ci: &ComponentInterface) -> Result<bool, askama::Error> {
        Ok(match type_ {
            Type::Object { .. } | Type::CallbackInterface { .. } => false,
            Type::Record { name, .. } => serializable_record(
                ci.get_record_definition(name)
                    .ok_or_else(|| to_askama_error(&format!("could not find record '{name}'")))?,
                ci,
            )?,
            Type::Enum { name, .. } => serializable_enum(
                ci.get_enum_definition(name)
                    .ok_or_else(|| to_askama_error(&format!("could not find enum '{name}'")))?,
                ci,
            )?,
            Type::Optional { inner_type } | Type::Sequence { inner_type } => {
                serializable_type(inner_type, ci)?
            }
            Type::Map {
                key_type,
                value_type,
            } => serializable_type(key_type, ci)? && serializable_type(value_type, ci)?,
            // Assume a custom type using a serializable type is also serializable.
            Type::Custom { builtin, .. } => serializable_type(builtin, ci)?,
            _ => true,
        })
    }

    pub fn serializable_record(
        record: &Record,
        ci: &ComponentInterface,
    ) -> Result<bool, askama::Error> {
        for field in record.fields() {
            if !serializable_type(&field.as_type(), ci)? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    pub fn serializable_enum(enum_: &Enum, ci: &ComponentInterface) -> Result<bool, askama::Error> {
        if ci.is_name_used_as_error(enum_.name()) {
            return Ok(false);
        }

        if enum_.is_flat() {
            let Some(variant_discr_type) = enum_.variant_discr_type() else {
                return Ok(true);
            };
            return serializable_type(variant_discr_type, ci);
        }

        // Unlike records or enum variants, if any of the variants are serializable, the
        // enum can be marked as serializable.
        for variant in enum_.variants() {
            if serializable_enum_variant(variant, ci)? {
                return Ok(true);
            }
        }
        Ok(false)
    }

    pub fn serializable_enum_variant(
        variant: &Variant,
        ci: &ComponentInterface,
    ) -> Result<bool, askama::Error> {
        for field in variant.fields() {
            if !serializable_type(&field.as_type(), ci)? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    pub fn ffi_type_name_by_value(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_type_label_by_value(type_, ci))
    }

    pub fn ffi_type_name(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_type_label(type_, ci))
    }

    pub fn ffi_as_callback(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<Option<FfiCallbackFunction>, askama::Error> {
        let FfiType::Callback(callback_name) = type_ else {
            return Ok(None);
        };

        for def in ci.ffi_definitions() {
            let FfiDefinition::CallbackFunction(callback) = def else {
                continue;
            };
            if callback.name() != callback_name {
                continue;
            }
            return Ok(Some(callback));
        }

        Err(to_askama_error(&format!(
            "could not find FFI callback '{callback_name}'"
        )))
    }

    /// Kotlin/Native's cinterop ignores nullability of parameters of callback definitions in
    /// headers. We need to determine whether a callback pointer needs to be casted before being
    /// passed to a FFI function.
    pub fn ffi_callback_needs_casting_native(
        ffi_callback: &FfiCallbackFunction,
    ) -> Result<bool, askama::Error> {
        Ok(ffi_callback.has_rust_call_status_arg()
            || ffi_callback.arguments().iter().any(|a| {
                matches!(
                    a.type_(),
                    FfiType::RustBuffer(_)
                        | FfiType::ForeignBytes
                        | FfiType::Callback(_)
                        | FfiType::Struct(_)
                )
            }))
    }

    /// Convert a local RustBuffer to an external RustBuffer.
    pub fn ffi_cast_to_external_rust_buffer_if_needed(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let FfiType::RustBuffer(Some(metadata)) = type_ else {
            return Ok(String::new());
        };
        if metadata.module_path == ci.crate_name() {
            return Ok(String::new());
        }
        Ok(format!(".as{}()", metadata.name))
    }

    /// Convert an external RustBuffer to a local RustBuffer.
    pub fn ffi_cast_to_local_rust_buffer_if_needed(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let FfiType::RustBuffer(Some(metadata)) = type_ else {
            return Ok(String::new());
        };
        if metadata.module_path == ci.crate_name() {
            return Ok(String::new());
        }
        Ok(format!(".from{}ToLocal()", metadata.name))
    }

    /// Append a `_` if the name is a valid c/c++ keyword
    pub fn header_escape_name(nm: &str) -> Result<String, askama::Error> {
        if CPP_KEYWORDS.contains(&nm) {
            Ok(format!("{nm}_"))
        } else {
            Ok(nm.to_owned())
        }
    }

    pub fn header_ffi_type_name(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_type_label_header(type_, ci))
    }

    pub fn ffi_type_name_for_ffi_struct(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_type_label_for_ffi_struct(type_, ci))
    }

    pub fn ffi_default_value(type_: FfiType) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_default_value(&type_))
    }

    /// Get the idiomatic Kotlin rendering of a function name.
    pub fn class_name<S: AsRef<str>>(
        nm: S,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.class_name(ci, nm.as_ref()))
    }

    /// Get the idiomatic Kotlin rendering of a function name.
    pub fn fn_name<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.fn_name(nm.as_ref()))
    }

    /// Get the idiomatic Kotlin rendering of a variable name.
    pub fn var_name<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.var_name(nm.as_ref()))
    }

    /// Get the idiomatic Kotlin rendering of a variable name.
    pub fn var_name_raw<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.var_name_raw(nm.as_ref()))
    }

    /// Get a String representing the name used for an individual enum variant.
    pub fn variant_name(v: &Variant, config: &Config) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.enum_variant_name(v.name(), config))
    }

    pub fn error_variant_name(v: &Variant) -> Result<String, askama::Error> {
        let name = v.name().to_string().to_upper_camel_case();
        Ok(KotlinCodeOracle.convert_error_suffix(&name))
    }

    /// Get the idiomatic Kotlin rendering of an FFI callback function name
    pub fn ffi_callback_name<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_callback_name(nm.as_ref()))
    }

    /// Get the idiomatic Kotlin rendering of an FFI struct name
    pub fn ffi_struct_name<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_struct_name(nm.as_ref()))
    }

    pub fn async_poll(
        callable: impl Callable,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_poll(ci);
        Ok(format!(
            "{{ future, callback, continuation -> UniffiLib.INSTANCE.{ffi_func}(future, callback, continuation) }}"
        ))
    }

    pub fn async_complete(
        callable: impl Callable,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_complete(ci);
        let call = format!("UniffiLib.INSTANCE.{ffi_func}(future, continuation)");
        // May need to convert the RustBuffer from our package to the RustBuffer of the external package
        let call = match callable.return_type() {
            Some(return_type) if ci.is_external(return_type) => {
                let ffi_type = FfiType::from(return_type);
                match ffi_type {
                    FfiType::RustBuffer(Some(ExternalFfiMetadata { name, .. })) => {
                        let suffix = KotlinCodeOracle.class_name(ci, &name);
                        format!("{call}.let {{ RustBuffer{suffix}ByValue(it.capacity, it.len, it.data) }}")
                    }
                    _ => call,
                }
            }
            _ => call,
        };
        Ok(format!("{{ future, continuation -> {call} }}"))
    }

    pub fn async_free(
        callable: impl Callable,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_free(ci);
        Ok(format!(
            "{{ future -> UniffiLib.INSTANCE.{ffi_func}(future) }}"
        ))
    }

    pub fn async_cancel(
        callable: impl Callable,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_cancel(ci);
        Ok(format!(
            "{{ future -> UniffiLib.INSTANCE.{ffi_func}(future) }}"
        ))
    }

    /// Remove the "`" chars we put around function/variable names
    ///
    /// These are used to avoid name clashes with kotlin identifiers, but sometimes you want to
    /// render the name unquoted.  One example is the message property for errors where we want to
    /// display the name for the user.
    pub fn unquote<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(nm.as_ref().trim_matches('`').to_string())
    }

    /// Get the idiomatic Kotlin rendering of docstring
    pub fn docstring<S: AsRef<str>>(docstring: S, spaces: &i32) -> Result<String, askama::Error> {
        let middle = textwrap::indent(&textwrap::dedent(docstring.as_ref()), " * ");
        let wrapped = format!("/**\n{middle}\n */");

        let spaces = usize::try_from(*spaces).unwrap_or_default();
        Ok(textwrap::indent(&wrapped, &" ".repeat(spaces)))
    }

    pub fn repeat(string: &str, n: &i32) -> Result<String, askama::Error> {
        let n = usize::try_from(*n).unwrap_or_default();
        Ok(string.repeat(n))
    }
}
