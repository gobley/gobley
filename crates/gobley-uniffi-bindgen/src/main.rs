/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::collections::HashMap;
use std::error::Error;
use std::fs;

use anyhow::Context as _;
use camino::{Utf8Path, Utf8PathBuf};
use clap::Parser;
use gobley_uniffi_bindgen::KotlinBindingGenerator;
use uniffi_bindgen::BindgenCrateConfigSupplier;

#[derive(Parser)]
#[clap(name = clap::crate_name!())]
#[clap(version = clap::crate_version!())]
#[clap(propagate_version = true)]
struct Cli {
    /// Directory in which to write generated files. Default is same folder as .udl file.
    #[clap(long, short)]
    out_dir: Option<Utf8PathBuf>,

    /// Path to the optional uniffi config file.
    /// If not provided, uniffi-bindgen will try to guess it from the UDL's file location.
    #[clap(long, short)]
    config: Option<Utf8PathBuf>,

    /// Path to the optional uniffi config file by a crate name.
    #[clap(long, value_parser = parse_key_val::<String, Utf8PathBuf>)]
    crate_configs: Vec<(String, Utf8PathBuf)>,

    /// Path of the package by a crate name.
    #[clap(long, value_parser = parse_key_val::<String, Utf8PathBuf>)]
    crate_paths: Vec<(String, Utf8PathBuf)>,

    /// Extract proc-macro metadata from a native lib (cdylib or staticlib) for this crate.
    #[clap(long, short)]
    lib_file: Option<Utf8PathBuf>,

    /// Pass in a cdylib path rather than a UDL file.
    #[clap(long = "library")]
    library_mode: bool,

    /// When `--library` is passed, only generate bindings for one crate.
    /// When `--library` is not passed, use this as the crate name instead of attempting to
    /// locate and parse Cargo.toml.
    #[clap(long = "crate")]
    crate_name: Option<String>,

    #[clap(long = "format", default_value_t = false)]
    try_format_code: bool,

    /// Path to the UDL file, or cdylib if `library-mode` is specified.
    source: Utf8PathBuf,
}

/// Parse a single key-value pair
fn parse_key_val<T, U>(s: &str) -> Result<(T, U), anyhow::Error>
where
    T: std::str::FromStr,
    T::Err: Error + Send + Sync + 'static,
    U: std::str::FromStr,
    U::Err: Error + Send + Sync + 'static,
{
    let pos = s
        .find('=')
        .ok_or_else(|| anyhow::Error::msg(format!("invalid key=value format: '{s}'")))?;
    Ok((s[..pos].parse()?, s[pos + 1..].parse()?))
}

pub struct CliCrateConfigSupplier {
    crate_configs: HashMap<String, Utf8PathBuf>,
    crate_pths: HashMap<String, Utf8PathBuf>,
}

impl BindgenCrateConfigSupplier for CliCrateConfigSupplier {
    fn get_toml(&self, crate_name: &str) -> anyhow::Result<Option<toml::value::Table>> {
        if let Some(path) = self.crate_configs.get(crate_name) {
            return load_toml_file(path);
        }
        if let Some(crate_path) = self.crate_pths.get(crate_name) {
            return load_toml_file(&crate_path.join("uniffi.toml"));
        }
        Ok(None)
    }

    fn get_udl(&self, crate_name: &str, udl_name: &str) -> anyhow::Result<String> {
        let path = self
            .crate_pths
            .get(crate_name)
            .context(format!("No path known to UDL files for '{crate_name}'"))?
            .join("src")
            .join(format!("{udl_name}.udl"));
        if path.exists() {
            Ok(fs::read_to_string(path)?)
        } else {
            anyhow::bail!(format!("No UDL file found at '{path}'"));
        }
    }
}

fn load_toml_file(path: &Utf8Path) -> anyhow::Result<Option<toml::value::Table>> {
    let contents = fs::read_to_string(path).with_context(|| format!("read file: {:?}", path))?;
    Ok(Some(
        toml::de::from_str(&contents).with_context(|| format!("parse toml: {:?}", path))?,
    ))
}

fn main() -> anyhow::Result<()> {
    let Cli {
        out_dir,
        config,
        crate_configs,
        crate_paths,
        lib_file,
        library_mode,
        crate_name,
        source,
        try_format_code,
    } = Cli::parse();

    let binding_generator = KotlinBindingGenerator;

    if library_mode {
        if lib_file.is_some() {
            panic!("--lib-file is not compatible with --library.")
        }
        let out_dir = out_dir.expect("--out-dir is required when using --library");

        uniffi_bindgen::library_mode::generate_bindings(
            &source,
            crate_name,
            &binding_generator,
            &CliCrateConfigSupplier {
                crate_configs: crate_configs.into_iter().collect(),
                crate_pths: crate_paths.into_iter().collect(),
            },
            config.as_deref(),
            &out_dir,
            try_format_code,
        )?;
    } else {
        uniffi_bindgen::generate_external_bindings(
            &binding_generator,
            source,
            config,
            out_dir,
            lib_file,
            crate_name.as_deref(),
            try_format_code,
        )?;
    }

    Ok(())
}
