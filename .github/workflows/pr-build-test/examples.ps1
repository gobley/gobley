$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

# Run build project-wise to prevent no space left errors
$projects = Get-ChildItem ./examples |
    ? { Test-Path "$_/build.gradle.kts" } |
    % { $_.Name };
try {
    foreach ($project in $projects) {
        try {
            ./gradlew ":examples:$project:build" `
                "-Pgobley.projects.gradleTests=false" `
                "-Pgobley.projects.uniffiTests=false";
        } finally {
            ./.github/workflows/pr-build-test/copy-test-result.ps1;
            ./gradlew ":examples:$project:build" `
                "-Pgobley.projects.gradleTests=false" `
                "-Pgobley.projects.uniffiTests=false";
        }
    }
    
} finally {
    ./gradlew clean `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.uniffiTests=false";
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}