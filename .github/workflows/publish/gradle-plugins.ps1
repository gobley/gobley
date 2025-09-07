$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

$projectNames = @(
    "gobley-gradle",
    "gobley-gradle-cargo",
    "gobley-gradle-rust",
    "gobley-gradle-uniffi"
);

foreach ($projectName in $projectNames) {
    & "./gradlew" ":build-logic:${projectName}:publishToMavenCentral" `
        "--no-configuration-cache";
}