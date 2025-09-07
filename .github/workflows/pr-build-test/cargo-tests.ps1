$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

${env:RUSTFLAGS} = "-D warnings";
cargo build --verbose;
cargo test --verbose;
cargo clean;