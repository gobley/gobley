param (
    [switch]$GenerateImmutableRecords,
    [switch]$OmitChecksums
);

$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

$testGroups = @($null, "extTypes", "futures");

try {
    $additionalArguments = @();
    foreach ($testGroup in $testGroups) {
        $basicTests = $null -eq $testGroup;
        $extTypes = "extTypes" -eq $testGroup;
        $futures = "futures" -eq $testGroup;
        if ($futures) { $additionalArguments += "--max-workers=1"; }
        try {
            ./gradlew build `
                $additionalArguments `
                "-Pgobley.projects.gradleTests=false" `
                "-Pgobley.projects.examples=false" `
                "-Pgobley.projects.uniffiTests=$basicTests" `
                "-Pgobley.projects.uniffiTests.extTypes=$extTypes" `
                "-Pgobley.projects.uniffiTests.futures=$futures" `
                "-Pgobley.projects.uniffiTests.generateImmutableRecords=$GenerateImmutableRecords" `
                "-Pgobley.projects.uniffiTests.omitChecksums=$OmitChecksums";
        } finally {
            # Allow overwriting the test result to use the last one.
            # If the test fails, this will copy the result of the failing tests.
            ./.github/workflows/pr-build-test/copy-test-result.ps1;
            ./gradlew --stop;
            ./gradlew clean `
                "-Pgobley.projects.gradleTests=false" `
                "-Pgobley.projects.examples=false" `
                "-Pgobley.projects.uniffiTests=$basicTests" `
                "-Pgobley.projects.uniffiTests.extTypes=$extTypes" `
                "-Pgobley.projects.uniffiTests.futures=$futures";
        }
    }
} finally {
    ./gradlew --stop;
    ./gradlew clean `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.examples=false";
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}
