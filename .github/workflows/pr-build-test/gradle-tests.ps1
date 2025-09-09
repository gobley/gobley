$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

try {
    ./gradlew build `
        "-Pgobley.projects.uniffiTests=false" `
        "-Pgobley.projects.uniffiTests.futures=false" `
        "-Pgobley.projects.examples=false";
} finally {
    ./.github/workflows/pr-build-test/copy-test-result.ps1;
    ./gradlew --stop;
    ./gradlew clean `
        "-Pgobley.projects.uniffiTests=false" `
        "-Pgobley.projects.uniffiTests.futures=false" `
        "-Pgobley.projects.examples=false";
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}
