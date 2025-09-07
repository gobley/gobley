$ErrorActionPreference = "Stop";

$error = false;
try {
    $projectNames = @(
        "gobley-gradle",
        "gobley-gradle-cargo",
        "gobley-gradle-rust",
        "gobley-gradle-uniffi"
    );
    foreach ($projectName in $projectNames) {
        & "./gradlew" ":build-logic:${projectName}:test";
        if ($LASTEXITCODE -ne 0) {
            $error = $true;
        }
    }
} finally {
    ./.github/workflows/pr-build-test/copy-test-result.ps1;
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
    if ($error) {
        exit 1;
    }
    exit 0;
}
