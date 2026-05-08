#!/bin/bash

# ==============================================================================
# RecoverBot Firmware Build Script (PlatformIO version)
#
# This script automates the resource generation and triggers PlatformIO build.
# Usage: ./build.sh [platformio-flags]
#
# Examples:
#   ./build.sh                    # Standard compile
#   ./build.sh -t upload          # Compile and upload
#   ./build.sh -t clean           # Clean build artifacts
# ==============================================================================

# Exit immediately if a command exits with a non-zero status
set -e

# Ensure the script executes from its own directory (firmware/)
cd "$(dirname "$0")"

# Resource configuration
IMG_SRC="logo_240.png"
HEADER_DEST="src/logo.h"
HEADER_GUARD="THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_LOGO_H_"

echo "========================================"
echo " 🚀 Starting Firmware Build (PlatformIO)"
echo "========================================"

# --- Step 1: Generate Header from Image ---
if command -v xxd >/dev/null 2>&1; then
    if [ -f "$IMG_SRC" ]; then
        echo "🎨 Converting $IMG_SRC -> $HEADER_DEST..."
        echo "#ifndef $HEADER_GUARD" > "$HEADER_DEST"
        echo "#define $HEADER_GUARD" >> "$HEADER_DEST"
        echo "" >> "$HEADER_DEST"
        xxd -i "$IMG_SRC" >> "$HEADER_DEST"
        echo "" >> "$HEADER_DEST"
        echo "#endif  // $HEADER_GUARD" >> "$HEADER_DEST"
        echo "✅ Header file generated successfully."
    else
        echo "⚠️  Warning: Source image '$IMG_SRC' not found. Skipping generation."
    fi
else
    echo "⚠️  Warning: 'xxd' tool not found. Skipping logo generation."
fi

# --- Step 2: Check for PlatformIO ---
VENV_DIR=".venv"
PIO_CMD="$VENV_DIR/bin/pio"

echo "🔧 Ensuring isolated environment in $VENV_DIR..."

if [ ! -d "$VENV_DIR" ]; then
    echo "📦 Creating virtual environment..."
    python3 -m venv "$VENV_DIR" --without-pip
fi

if [ ! -f "$VENV_DIR/bin/pip" ]; then
    echo "📥 Bootstrapping pip..."
    curl -sSL https://bootstrap.pypa.io/get-pip.py -o /tmp/get-pip.py
    "$VENV_DIR/bin/python3" /tmp/get-pip.py
    rm /tmp/get-pip.py
fi

if [ ! -f "$PIO_CMD" ]; then
    echo "🚀 Installing PlatformIO..."
    "$VENV_DIR/bin/pip" install platformio
fi

# --- Step 3: Compile / Upload ---

# If the first argument is 'run', shift it away to avoid 'pio run run'
if [ "$1" = "run" ]; then
    shift
fi

echo "🔨 Running PlatformIO with args: $@"
echo "----------------------------------------"

"$PIO_CMD" run "$@"

echo "----------------------------------------"
echo " 🎉 Build Process Finished!"
echo "========================================"
