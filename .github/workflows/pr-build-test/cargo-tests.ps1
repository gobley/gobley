$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

${env:RUSTFLAGS} = "-D warnings";
cargo build --verbose;
cargo test --verbose;
cargo clean;