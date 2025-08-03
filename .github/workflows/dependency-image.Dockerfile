FROM rust:1.82.0-slim

# Use Clippy to detect more warnings
RUN rustup component add clippy

# Install various dependencies
RUN apt update && \
    apt install -y \
      openjdk-17-jdk-headless \
      build-essential \
      binutils \
      # For Android
      sdkmanager \
      # For MinGW
      gcc-mingw-w64 \
      mingw-w64-x86-64-dev \
      # For Linux example apps
      libgtk-4-dev \
      # For Zig and PowerShell
      wget \
      # For packages that builds OpenSSL from the source like blake3 used in examples
      perl \
      # For Boring SSL
      git cmake libclang-dev && \
    # Use Chrome for WASM/JS testing
    apt install -y \
      fonts-liberation \
      libatk-bridge2.0-0 \
      libatk1.0-0 \
      libatspi2.0-0 \
      libcurl4 \
      xdg-utils && \
    wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb && \
      dpkg -i google-chrome-stable_current_amd64.deb && \
      rm google-chrome-stable_current_amd64.deb && \
    apt install -y google-chrome-stable && \
    # Use PowerShell for complex scripting
    . /etc/os-release && \
      wget -q https://packages.microsoft.com/config/debian/$VERSION_ID/packages-microsoft-prod.deb && \
      dpkg -i packages-microsoft-prod.deb && \
      rm packages-microsoft-prod.deb && \
    apt update && \
    apt install -y powershell && \
    # Delete package resource info as not needed
    rm -rf /var/lib/apt/lists/*

# Use Zig as cross-compilation linker for Arm64 Linux
ENV ZIG_VERSION=0.13.0
ENV ZIG_BUILD=zig-linux-x86_64-$ZIG_VERSION
RUN mkdir -p /zig
WORKDIR /zig
RUN wget -c https://ziglang.org/download/$ZIG_VERSION/$ZIG_BUILD.tar.xz && \
    tar -xf $ZIG_BUILD.tar.xz && \
    rm $ZIG_BUILD.tar.xz && \
    printf "#! /bin/sh\n/zig/$ZIG_BUILD/zig cc -target aarch64-linux-gnu \"\$@\"" > aarch64-unknown-linux-gnu-cc.sh && \
    chmod 555 aarch64-unknown-linux-gnu-cc.sh && \
    mkdir -p /.cargo && \
    printf "[target.aarch64-unknown-linux-gnu]\nlinker = \"/zig/aarch64-unknown-linux-gnu-cc.sh\"" > /.cargo/config.toml

# Configure the Android SDK
ENV ANDROID_HOME=/android-sdk
RUN mkdir -p $ANDROID_HOME
RUN sdkmanager --install \
    "cmake;3.22.1" \
    "build-tools;34.0.0" \
    "build-tools;35.0.1" \
    "platform-tools;35.0.2" \
    "platforms;android-35" \
    "ndk;27.0.12077973"
# RUN mkdir -p $ANDROID_HOME/licenses && \
#     touch $ANDROID_HOME/licenses/android-sdk-license && \
#     echo "8933bad161af4178b1185d1a37fbf41ea5269c55" >> $ANDROID_HOME/licenses/android-sdk-license && \
#     echo "d56f5187479451eabf01fb78af6dfcb131a6481e" >> $ANDROID_HOME/licenses/android-sdk-license && \
#     echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" >> $ANDROID_HOME/licenses/android-sdk-license && \
#     yes | sdkmanager --licenses

# Download dependencies to warm the cache
COPY . /dependency-hot-plate
WORKDIR /dependency-hot-plate
RUN ./gradlew dependencies

# Delete the project files so they don't mess with future builds
WORKDIR /
RUN rm -rf /dependency-hot-plate
