$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

if ($IsWindows) {
    choco install -y mingw;
    # Required by :examples:tokio-blake3-app to use Perl to build OpenSSL
    choco install -y msys2;
    # Check if OpenSSL will complain about backslashes with the perl shipped with MSYS2
    & "C:\tools\msys64\usr\bin\perl.exe" -MFile::Spec::Functions=rel2abs,abs2rel -e "abs2rel(rel2abs('.')) =~ m{\\\\} and die 'This perl does not produce Unix-like paths';"
    # Prepend the path to the directory containing MSYS2 Perl to PATH
    "C:\tools\msys64\usr\bin" | Out-File -FilePath $env:GITHUB_PATH -Encoding utf8 -Append;
    # Required by :tests:gradle:android-linking
    choco install -y nasm;
} elseif ($IsMacOS) {
    brew update;
    brew install mingw-w64;
    # Workaround for #205
    rustup install nightly;
    rustup component add rust-src --toolchain nightly;
} elseif ($IsLinux) {
    sudo apt-get update;
    sudo apt-get install -y mingw-w64;
}
