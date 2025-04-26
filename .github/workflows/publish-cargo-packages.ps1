$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

if (${env:GITHUB_REF}.EndsWith("-SNAPSHOT")) {
    Write-Warning "SNAPSHOT publish is not available for Cargo packages.";
    exit 0;
}

${env:RUSTFLAGS} = "-D warnings";
cargo login "${env:GOBLEY_CRATES_IO_API_TOKEN}";

$packageNames = @(
    "gobley-uniffi-bindgen"
);

# Run checks before publishing
foreach ($packageName in $packageNames) {
    & "cargo" "publish" "--dry-run" "-p" $packageName;
}

# Publish after checks
foreach ($packageName in $packageNames) {
    & "cargo" "publish" "-p" $packageName;
}