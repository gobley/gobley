$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

try {
    ./gradlew check `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.examples=false" `
        "-Pgobley.projects.uniffiTests.generateImmutableRecords=false" `
        "-Pgobley.projects.uniffiTests.omitChecksums=false";
    ./gradlew check `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.examples=false" `
        "-Pgobley.projects.uniffiTests.generateImmutableRecords=true" `
        "-Pgobley.projects.uniffiTests.omitChecksums=false";
    ./gradlew check `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.examples=false" `
        "-Pgobley.projects.uniffiTests.generateImmutableRecords=false" `
        "-Pgobley.projects.uniffiTests.omitChecksums=true";
    ./gradlew check `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.examples=false" `
        "-Pgobley.projects.uniffiTests.generateImmutableRecords=true" `
        "-Pgobley.projects.uniffiTests.omitChecksums=true";
} finally {
    ./.github/workflows/pr-build-test-copy-test-result.ps1;
    ./gradlew clean `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.examples=false";
    ./.github/workflows/pr-build-test-change-file-owner.ps1;
}
