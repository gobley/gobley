param(
    [Parameter(Mandatory=$true)]
    [string[]]$TestNames
);

$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

$knownTests = @("app", "audioCppApp", "customTypes", "tokioBlake3App", "tokioBoringApp");
foreach ($testName in $TestNames) {
    if (-not ($knownTests -contains $testName)) {
        throw "$testName is not a valid test name. Possible values: $knownTests";
    }
}
$arguments = $knownTests | % {
    $enable = ($TestNames -contains $_).ToString().ToLower();
    "-Pgobley.projects.examples.${_}=$enable"
};

try {
    ./gradlew build `
        $arguments `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.uniffiTests=false" `
        "-Pgobley.projects.uniffiTests.extTypes=false" `
        "-Pgobley.projects.uniffiTests.futures=false";
} finally {
    ./gradlew --stop;
    ./gradlew clean `
        $arguments `
        "-Pgobley.projects.gradleTests=false" `
        "-Pgobley.projects.uniffiTests=false" `
        "-Pgobley.projects.uniffiTests.extTypes=false" `
        "-Pgobley.projects.uniffiTests.futures=false";
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}

# Build Xcode projects
if ($IsMacOS) {
    foreach ($testName in $TestNames) {
        $xcodeSchemeNames = switch ($testName) {
            "app" {
                @(
                    "ExamplesApp (iOS)",
                    "ExamplesApp (macOS)",
                    "ExamplesApp (tvOS)",
                    "ExamplesApp (watchOS)"
                )
            }
            "audioCppApp" { @("AudioCppApp") }
            "tokioBlake3App" { @("TokioBlake3App") }
            "tokioBoringApp" { @("TokioBoringApp") }
            default { @() }
        };
        foreach ($xcodeSchemeName in $xcodeSchemeNames) {
            xcodebuild `
                -sdk iphonesimulator `
                -workspace "examples/Examples.xcworkspace" `
                -scheme $xcodeSchemeName;
        }
    }
}