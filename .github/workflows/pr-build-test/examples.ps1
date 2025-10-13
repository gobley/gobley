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
    ./.github/workflows/pr-build-test/copy-test-result.ps1;
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
    ${env:GOBLEY_XCODE_CONFIGURE_OWN_GRADLE_PROJECT} = "true";
    foreach ($testName in $TestNames) {
        $xcodeSchemes = switch ($testName) {
            "app" {
                @(
                    @{ Name = "ExamplesApp (iOS)"; Sdk = "iphoneos" },
                    @{ Name = "ExamplesApp (iOS)"; Sdk = "iphonesimulator" },
                    @{ Name = "ExamplesApp (macOS)"; Sdk = "macosx" },
                    @{ Name = "ExamplesApp (tvOS)"; Sdk = "appletvos" },
                    @{ Name = "ExamplesApp (tvOS)"; Sdk = "appletvsimulator" },
                    @{ Name = "ExamplesApp (watchOS)"; Sdk = "watchos" },
                    @{ Name = "ExamplesApp (watchOS)"; Sdk = "watchsimulator" }
                )
            }
            "audioCppApp" {
                @(
                    @{ Name = "AudioCppApp"; Sdk = "iphoneos" },
                    @{ Name = "AudioCppApp"; Sdk = "iphonesimulator" }
                )
            }
            "tokioBlake3App" {
                @(
                    @{ Name = "TokioBlake3App"; Sdk = "iphoneos" },
                    @{ Name = "TokioBlake3App"; Sdk = "iphonesimulator" }
                )
            }
            "tokioBoringApp" {
                @(
                    @{ Name = "TokioBoringApp"; Sdk = "iphoneos" },
                    @{ Name = "TokioBoringApp"; Sdk = "iphonesimulator" }
                )
            }
            default { @() }
        };
        foreach ($xcodeScheme in $xcodeSchemes) {
            xcodebuild `
                -sdk $xcodeScheme.Sdk `
                -workspace "examples/Examples.xcworkspace" `
                -scheme $xcodeScheme.Name `
                CODE_SIGNING_ALLOWED=NO;
        }
    }
}