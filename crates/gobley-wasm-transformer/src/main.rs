/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::fs::{self, File};
use std::io::{BufRead, BufReader};
use std::str::FromStr as _;

use anyhow::Context;
use camino::Utf8PathBuf;
use clap::Parser;
use gobley_wasm_transformer::import::WasmFunctionImport;
use gobley_wasm_transformer::Transformer;

#[derive(Parser)]
#[clap(name = clap::crate_name!())]
#[clap(version = clap::crate_version!())]
#[clap(propagate_version = true)]
struct Cli {
    /// The path to the .wasm file to transform.
    #[clap(long, short)]
    input: Utf8PathBuf,

    /// The path where the output .kt file will be generated.
    #[clap(long, short)]
    output: Utf8PathBuf,

    /// The package name to be used in the resulting .kt file.
    #[clap(long, short)]
    package_name: Option<String>,

    /// The path to the file containing the list of line-separated additional function imports to be
    /// inserted into the transformed WASM module.
    #[clap(long, short)]
    function_imports_file: Option<Utf8PathBuf>,
}

fn main() -> anyhow::Result<()> {
    let Cli {
        input,
        output,
        package_name,
        function_imports_file: function_imports_file_path,
    } = Cli::parse();
    let input = fs::read(&input).with_context(|| format!("failed to read `{input}`"))?;

    let mut function_imports = vec![];
    if let Some(function_imports_file_path) = function_imports_file_path {
        let function_imports_file = File::open(&function_imports_file_path)
            .with_context(|| format!("failed to open `{function_imports_file_path}`"))?;
        for line in BufReader::new(function_imports_file).lines() {
            let line = line.with_context(|| {
                format!("failed to read lines from `{function_imports_file_path}`")
            })?;
            let line = line.trim();
            if !line.is_empty() {
                let function_import = WasmFunctionImport::from_str(line).with_context(|| {
                    format!(
                "failed to read function import definitions from `{function_imports_file_path}`"
            )
                })?;
                function_imports.push(function_import);
            }
        }
    }

    let transformer = Transformer::new(&input, function_imports)?;
    let output_kt = transformer.render_into_kt(package_name.as_deref())?;
    fs::write(&output, output_kt).with_context(|| format!("failed to write `{output}`"))?;
    Ok(())
}
