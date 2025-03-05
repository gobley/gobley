$projectNames = @(
    "gobley-gradle",
    "gobley-gradle-cargo",
    "gobley-gradle-rust",
    "gobley-gradle-uniffi"
);
foreach ($projectName in $projectNames) {
    & "./gradlew" ":build-logic:${projectName}:test";
}
./.github/workflows/pr-build-test-copy-test-result.ps1;