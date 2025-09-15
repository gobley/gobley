$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

if ($IsMacOS) {
    $currentArchitecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture;
    if ($currentArchitecture -eq [System.Runtime.InteropServices.Architecture]::X64) {
        # Disable Gradle Daemon on Intel macOS
        ${env:ORG_GRADLE_PROJECT_org.gradle.daemon} = "false";
        # Increase the metaspace size to 3GB on Intel macOS
        ${env:ORG_GRADLE_PROJECT_org.gradle.jvmargs} = "-Xmx6g -XX:MaxMetaspaceSize=3g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8";
        # Disable Gradle configuration cache on Intel macOS
        ${env:GRADLE_OPTS} = "-Dorg.gradle.configuration-cache=false";
    }
}
