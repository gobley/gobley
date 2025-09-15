$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

# Disable Gradle Daemon on Intel macOS and increase the metaspace size to 3GB
if ($IsMacOS) {
    $currentArchitecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture;
    if ($currentArchitecture -eq [System.Runtime.InteropServices.Architecture]::X64) {
        ${env:ORG_GRADLE_PROJECT_org.gradle.daemon} = "false";
        ${env:ORG_GRADLE_PROJECT_org.gradle.jvmargs} = "-Xmx6g -XX:MaxMetaspaceSize=3g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8";
    }
}
