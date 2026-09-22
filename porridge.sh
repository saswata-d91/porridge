#!/bin/bash
set -e

# Version requirements
REQUIRED_JAVA_VERSION=17
JRE_DIR="$HOME/.porridge/jre"

get_java_version() {
    if command -v java >/dev/null 2>&1; then
        java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{if ($1 == 1) print $2; else print $1}'
    else
        echo "0"
    fi
}

install_java() {
    echo "[SYSTEM] Java $REQUIRED_JAVA_VERSION or higher not found. Downloading JRE..."
    mkdir -p "$JRE_DIR"
    
    OS=$(uname -s | tr '[:upper:]' '[:lower:]')
    ARCH=$(uname -m)
    
    if [ "$ARCH" = "x86_64" ]; then ARCH="x64"; fi
    if [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then ARCH="aarch64"; fi
    if [ "$OS" = "darwin" ]; then OS="mac"; fi

    DOWNLOAD_URL="https://api.adoptium.net/v3/binary/latest/17/ga/$OS/$ARCH/jre/hotspot/normal/eclipse"
    
    TAR_FILE="$JRE_DIR/jre.tar.gz"
    curl -L -o "$TAR_FILE" "$DOWNLOAD_URL"
    
    echo "[SYSTEM] Extracting JRE..."
    tar -xzf "$TAR_FILE" -C "$JRE_DIR" --strip-components=1
    rm "$TAR_FILE"
}

# 1. Check Java
CURRENT_JAVA_VERSION=$(get_java_version)

if [ "$CURRENT_JAVA_VERSION" -ge "$REQUIRED_JAVA_VERSION" ]; then
    JAVA_CMD="java"
else
    if [ "$OS" = "darwin" ] || [ "$(uname -s)" = "Darwin" ]; then
        JAVA_CMD="$JRE_DIR/Contents/Home/bin/java"
    else
        JAVA_CMD="$JRE_DIR/bin/java"
    fi
    
    if [ ! -f "$JAVA_CMD" ]; then
        install_java
    fi
fi

# 2. Check JAR
JAR_FILE="porridge-core/target/claude-code-java-harness-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "[SYSTEM] Building Porridge..."
    if command -v mvn >/dev/null 2>&1; then
        mvn clean package -DskipTests
    else
        echo "[ERROR] Maven is not installed. Please build the project manually."
        exit 1
    fi
fi

# 3. Execute
exec "$JAVA_CMD" -jar "$JAR_FILE" "$@"
