$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

choco install -y mingw;
# Required by :examples:tokio-blake3-app to build OpenSSL
choco install -y strawberryperl;
# Required by :tests:gradle:android-linking
choco install -y nasm;
