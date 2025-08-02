/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::fs;

use anyhow::Context;
use camino::Utf8PathBuf;
use clap::Parser;
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
}

fn main() -> anyhow::Result<()> {
    let Cli {
        input,
        output,
        package_name,
    } = Cli::parse();
    let input = fs::read(&input).with_context(|| format!("failed to read `{input}`"))?;
    let transformer = Transformer::new(&input)?;
    let output_kt = transformer.transform_into_kt(package_name.as_deref())?;
    fs::write(&output, output_kt).with_context(|| format!("failed to write `{output}`"))?;
    Ok(())
}
