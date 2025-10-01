$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

# Run build project-wise to prevent no space left errors
$projects = Get-ChildItem ./examples |
    ? { Test-Path "$_/build.gradle.kts" } |
    % { $_.Name };
try {
    foreach ($project in $projects) {
        try {
            ./gradlew ":examples:${project}:build" `
                "-Pgobley.projects.gradleTests=false" `
                "-Pgobley.projects.uniffiTests=false" `
                "-Pgobley.projects.uniffiTests.extTypes=false" `
                "-Pgobley.projects.uniffiTests.futures=false";
        } finally {
            ./.github/workflows/pr-build-test/copy-test-result.ps1;
            ./gradlew --stop;
            ./gradlew ":examples:${project}:clean" `
                "-Pgobley.projects.gradleTests=false" `
                "-Pgobley.projects.uniffiTests=false" `
                "-Pgobley.projects.uniffiTests.extTypes=false" `
                "-Pgobley.projects.uniffiTests.futures=false";
        }
    }
    
} finally {
    ./gradlew --stop;
    ./gradlew clean `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.uniffiTests=false" `
        "-Pgobley.projects.uniffiTests.extTypes=false" `
        "-Pgobley.projects.uniffiTests.futures=false";
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}