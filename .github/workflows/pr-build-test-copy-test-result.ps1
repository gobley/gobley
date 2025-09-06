$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

# Copy build results
if (-not (Test-Path "./.github/workflows/pr-build-test")) {
    New-Item -Type Directory "./.github/workflows/pr-build-test" | Out-Null;
}
$testResultDirectories = Get-ChildItem . -Attributes Directory -Recurse |
    ? { $_.name -eq "test-results" }
foreach ($testResultDirectory in $testResultDirectories) {
    $relativePath = Resolve-Path $testResultDirectory -Relative;
    $parentPath = Split-Path $relativePath;
    $destination = Join-Path "./.github/workflows/pr-build-test" $parentPath;
    if (-not (Test-Path $destination)) {
        New-Item -Type Directory $destination | Out-Null;
    }
    Write-Host "$testResultDirectory -> $destination"
    Move-Item $testResultDirectory $destination;
}
