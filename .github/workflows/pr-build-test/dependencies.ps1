$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

if ($IsWindows) {
    choco install -y mingw;
    # Required by :examples:tokio-blake3-app to build OpenSSL
    choco install -y strawberryperl;
    # Required by :tests:gradle:android-linking
    choco install -y nasm;
} elseif ($IsMacOS) {
    brew update;
    brew install mingw-w64;
} elseif ($IsLinux) {
    sudo apt-get update;
    sudo apt-get install -y mingw-w64;
}
