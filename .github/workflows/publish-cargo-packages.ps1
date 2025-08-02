$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

${env:RUSTFLAGS} = "-D warnings";
cargo login "${env:GOBLEY_CRATES_IO_API_TOKEN}";

$packageNames = @(
    "gobley-uniffi-bindgen",
    "gobley-wasm-transformer"
);

# Run checks before publishing
foreach ($packageName in $packageNames) {
    & "cargo" "publish" "--dry-run" "-p" $packageName;
}

# Publish after checks
foreach ($packageName in $packageNames) {
    & "cargo" "publish" "-p" $packageName;
}