$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

# Disable Gradle Daemon on Intel macOS
if ($IsMacOS) {
    $currentArchitecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture;
    if ($currentArchitecture -eq [System.Runtime.InteropServices.Architecture]::X64) {
        ${env:ORG_GRADLE_PROJECT_org.gradle.daemon} = "false";
    }
}
