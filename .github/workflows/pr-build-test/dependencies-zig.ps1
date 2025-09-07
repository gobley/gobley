using namespace System.Runtime.InteropServices;

$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

$osArch = [RuntimeInformation]::OSArchitecture

# Determine the host tag
$zigHost = if ($IsWindows) {
    switch ($osArch) {
        "X64" { "x86_64-windows" }
        "Arm64" { "aarch64-windows" }
        default { $null }
    }
}
elseif ($IsMacOS) {
    switch ($osArch) {
        "X64" { "x86_64-macos" }
        "Arm64" { "aarch64-macos" }
        default { $null }
    }
}
elseif ($IsLinux) {
    switch ($osArch) {
        "X64" { "x86_64-linux" }
        "Arm64" { "aarch64-linux" }
        default { $null }
    }
}
else {
    $null
};

if ($null -eq $zigHost) {
    $osDescription = [RuntimeInformation]::OSDescription;
    throw "unsupported platform: $osDescription";
}

# Common variables
$zigVersion = "0.15.1";
$zigArchiveFormat = if ($IsWindows) { "zip" } else { "tar.xz" };
$zigCompilerScriptFormat = if ($IsWindows) { "bat" } else { "sh" };
$zigBuild = "zig-$zigHost-$zigVersion";
$zigBuildUrl = "https://ziglang.org/download/$zigVersion/$zigBuild.$zigArchiveFormat";
$zigHome = Join-Path $HOME "zig";
$zigBinary = if ($IsWindows) {
    Join-Path $zigHome $zigBuild "zig.exe"
} else {
    Join-Path $zigHome $zigBuild "zig"
};
$zigAndRustTargets = @(
    @{ Zig = "aarch64-linux-gnu"; Rust = "aarch64-unknown-linux-gnu" },
    @{ Zig = "x86_64-linux-gnu"; Rust = "x86_64-unknown-linux-gnu" }
    # @{ Zig = "aarch64-windows-gnu"; Rust = "aarch64-pc-windows-gnu" }
    # @{ Zig = "x86_64-windows-gnu"; Rust = "x86_64-pc-windows-gnu" }
);

# Download Zig and unzip
New-Item -Type Directory -Path $zigHome -Force | Out-Null;
try {
    Push-Location $zigHome;
    Write-Host "Downloading Zig...";
    Invoke-WebRequest -Uri $zigBuildUrl -OutFile "$zigBuild.$zigArchiveFormat";
    Write-Host "Unzipping Zig...";
    if ($zigArchiveFormat -eq "tar.xz") {
        tar -xf "$zigBuild.$zigArchiveFormat";
    } else {
        Expand-Archive -Path "$zigBuild.$zigArchiveFormat" -DestinationPath ".";
    }
    Remove-Item "$zigBuild.$zigArchiveFormat";
} finally {
    Pop-Location;
}

$actualZigVersion = & $zigBinary version;
Write-Host "Zig installed: $actualZigVersion";

# Prepare compilation test file
$compilationTestDirectory = Join-Path $zigHome "test-compilation";
$compilationTestCFilePath = Join-Path $compilationTestDirectory "main.c";
$compilationTestRustFilePath = Join-Path $compilationTestDirectory "main.rs";
try {
    New-Item -Type Directory -Path $compilationTestDirectory -Force | Out-Null;
    "int main() {}" | Out-File -FilePath $compilationTestCFilePath -Encoding utf8;
    "fn main() {}" | Out-File -FilePath $compilationTestRustFilePath -Encoding utf8;

    # Generate linker shell scripts that forwards command-line arguments to Zig
    foreach ($zigAndRustTarget in $zigAndRustTargets) {
        $zigTarget = $zigAndRustTarget.Zig;
        $rustTarget = $zigAndRustTarget.Rust;
        Write-Host "Configuring linking using Zig for the $rustTarget target...";
        $zigCompilerScript = if ($IsWindows) {
            "@echo off`n" +
            "$zigBinary cc -target $zigTarget %*"
        } else {
            "#! /bin/sh`n" +
            "$zigBinary cc -target $zigTarget `"`$@`""
        };
        $zigCompilerScriptPath = Join-Path $zigHome "$rustTarget.$zigCompilerScriptFormat";
        $zigCompilerScript | Out-File -FilePath $zigCompilerScriptPath -Encoding utf8;
        $zigCompilerScriptPath = (Resolve-Path $zigCompilerScriptPath).Path;
        if (-not $IsWindows) {
            chmod 555 $zigCompilerScriptPath;
        }
        $zigCompilerScriptPathEscaped = $zigCompilerScriptPath.Replace("\", "\\");
        $cargoConfigAddition = (
            "[target.$rustTarget]`n" +
            "linker = `"$zigCompilerScriptPathEscaped`"`n" +
            "`n"
        );
        $cargoConfigAddition | Out-File -FilePath "$HOME/.cargo/config.toml" -Encoding utf8 -Append;

        # Try compile a C and a Rust program using the linker shell script
        $rustTargetInstalled = $false;
        try {
            Push-Location $compilationTestDirectory;
            # Compile a C program using the shell script
            Write-Host "Building a test C program for $rustTarget using Zig...";
            & $zigCompilerScriptPath $compilationTestCFilePath;

            # Install the Rust target and Compile a Rust program linked using the shell script
            Write-Host "Installting a Rust target $rustTarget...";
            & "rustup" "target" "add" $rustTarget;
            $rustTargetInstalled = $true;
            Write-Host "Building a test Rust program for $rustTarget linked using Zig...";
            & "rustc" $compilationTestRustFilePath `
                "--target" $rustTarget         `
                "-Clinker=$zigCompilerScriptPath";
        } finally {
            # Remove the target so the RustUpTargetAddTask Gradle task can be tested properly
            if ($rustTargetInstalled) {
                & "rustup" "target" "remove" $rustTarget;
            }
            Pop-Location;
        }
        "CC_=$zigCompilerScriptPath" | Out-File -FilePath $env:GITHUB_OUTPUT -Encoding utf8 -Append;
        Write-Host "Configuration for $rustTarget done.";
    }
} finally {
    Remove-Item $compilationTestDirectory -Recurse;
}
