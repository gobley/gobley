$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

$configurations = @(
    @{ GenerateImmutableRecords = $false; OmitChecksums = $false; Futures = $false },
    @{ GenerateImmutableRecords = $true; OmitChecksums = $false; Futures = $false },
    @{ GenerateImmutableRecords = $false; OmitChecksums = $true; Futures = $false },
    @{ GenerateImmutableRecords = $true; OmitChecksums = $true; Futures = $false },
    @{ GenerateImmutableRecords = $false; OmitChecksums = $false; Futures = $true },
    @{ GenerateImmutableRecords = $true; OmitChecksums = $false; Futures = $true },
    @{ GenerateImmutableRecords = $false; OmitChecksums = $true; Futures = $true },
    @{ GenerateImmutableRecords = $true; OmitChecksums = $true; Futures = $true }
);

try {
    foreach ($configuration in $configurations) {
        $generateImmutableRecords = $configuration.GenerateImmutableRecords;
        $omitChecksums = $configuration.OmitChecksums;
        $futures = $configuration.Futures;
        $additionalArguments = @();
        if ($futures) { $additionalArguments += "--max-workers=1"; }
        ./gradlew build `
            $additionalArguments `
            "-Pgobley.projects.gradleTests=false" `
            "-Pgobley.projects.examples=false" `
            "-Pgobley.projects.uniffiTests=$(-not $futures)" `
            "-Pgobley.projects.uniffiTests.futures=$futures" `
            "-Pgobley.projects.uniffiTests.generateImmutableRecords=$generateImmutableRecords" `
            "-Pgobley.projects.uniffiTests.omitChecksums=$omitChecksums";
    }
} finally {
    ./.github/workflows/pr-build-test/copy-test-result.ps1;
    ./gradlew --stop;
    ./gradlew clean `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.examples=false";
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}
