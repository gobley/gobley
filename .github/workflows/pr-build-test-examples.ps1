$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

try {
    ./gradlew build `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.uniffiTests=false";
} finally {
    ./.github/workflows/pr-build-test-copy-test-result.ps1;
    ./gradlew clean `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.uniffiTests=false";
    ./.github/workflows/pr-build-test-change-file-owner.ps1;
}
