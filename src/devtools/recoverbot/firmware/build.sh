#!/bin/bash

# ==============================================================================
# RecoverBot Firmware Build Script
#
# This script automates the build environment setup and resource generation.
# Usage: ./compile.sh [arduino-cli-flags]
#
# Examples:
#   ./compile.sh                    # Standard compile
#   ./compile.sh --verbose          # Compile with verbose logging
#   ./compile.sh --clean            # Clean build cache before compiling
#   ./compile.sh --upload -p /dev/ttyUSB0  # Compile and upload to board
# ==============================================================================

# Exit immediately if a command exits with a non-zero status
set -e

# Ensure the script executes from its own directory (firmware/)
cd "$(dirname "$0")"

# --- Configuration ---
# Command to run arduino-cli (defaulting to global install)
CLI_CMD="arduino-cli"
# Directory for local tool installation
LOCAL_BIN_DIR=".bin"

# Resource configuration
IMG_SRC="logo_240.png"
HEADER_DEST="src/logo.h"
HEADER_GUARD="THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_LOGO_H_"

echo "========================================"
echo " 🚀 Starting Firmware Build"
echo "========================================"

# --- Step 1: Check for arduino-cli ---
# Check if arduino-cli is installed globally or locally.
# If missing, download it to the local .bin directory.

if ! command -v $CLI_CMD &> /dev/null; then
    # Check if we have a local copy
    if [ -f "$LOCAL_BIN_DIR/arduino-cli" ]; then
        CLI_CMD="$LOCAL_BIN_DIR/arduino-cli"
        echo "✅ Found local arduino-cli in $LOCAL_BIN_DIR"
    else
        echo "⚠️  arduino-cli not found globally. Installing locally..."
        mkdir -p "$LOCAL_BIN_DIR"

        # Download official installer and run it
        curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | BINDIR="$LOCAL_BIN_DIR" sh

        CLI_CMD="$LOCAL_BIN_DIR/arduino-cli"
        echo "✅ Installation complete."
    fi
else
    echo "✅ Found global arduino-cli."
fi

# --- Step 2: Generate Header from Image ---
# Convert the binary image file into a C header file using xxd.
# Checks if 'xxd' is installed first. If not, skips generation to avoid crashing.

if command -v xxd >/dev/null 2>&1; then
    # xxd exists, proceed with generation logic
    if [ -f "$IMG_SRC" ]; then
        echo "🎨 Converting $IMG_SRC -> $HEADER_DEST..."

        # 1. Write the opening Header Guard
        echo "#ifndef $HEADER_GUARD" > "$HEADER_DEST"
        echo "#define $HEADER_GUARD" >> "$HEADER_DEST"
        echo "" >> "$HEADER_DEST"

        # 2. Convert image to hex array
        # 'xxd -i' automatically uses the filename for the variable name.
        # logo_240.png -> unsigned char logo_240_png[]
        xxd -i "$IMG_SRC" >> "$HEADER_DEST"

        # 3. Write the closing Header Guard
        echo "" >> "$HEADER_DEST"
        echo "#endif  // $HEADER_GUARD" >> "$HEADER_DEST"

        echo "✅ Header file generated successfully."
    else
        echo "⚠️  Warning: Source image '$IMG_SRC' not found. Skipping generation."
    fi
else
    # xxd does NOT exist
    echo "⚠️  Warning: 'xxd' tool not found. Skipping logo generation."
    echo "    Using existing $HEADER_DEST (if present)."
fi

# --- Step 3: Compile / Upload ---
# Pass all command line arguments ("$@") directly to the compile command.
# This allows usage like: ./compile.sh --upload --port ...

echo "🔨 Running arduino-cli compile with args: $@"
echo "----------------------------------------"

"$CLI_CMD" compile "$@"

echo "----------------------------------------"
echo " 🎉 Build Process Finished!"
echo "========================================"
