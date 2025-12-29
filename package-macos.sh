#!/bin/bash
# Package FTX1-Hamlib as a macOS .app bundle
# Requires: JDK 14+ with jpackage

set -e

VERSION="1.2.0"
APP_NAME="FTX1-Hamlib"
MAIN_CLASS="com.yaesu.hamlib.HamlibFTX1"
JAR_FILE="target/ftx1-hamlib-${VERSION}.jar"
OUTPUT_DIR="target/package"

echo "Building ${APP_NAME} v${VERSION} for macOS..."

# Clean previous builds
rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"

# Check if JAR exists
if [ ! -f "${JAR_FILE}" ]; then
    echo "JAR not found. Building..."
    mvn clean package -DskipTests
fi

# Create app bundle with jpackage
echo "Creating macOS app bundle..."
jpackage \
    --input target \
    --dest "${OUTPUT_DIR}" \
    --name "${APP_NAME}" \
    --main-jar "ftx1-hamlib-${VERSION}.jar" \
    --main-class "${MAIN_CLASS}" \
    --type app-image \
    --app-version "${VERSION}" \
    --vendor "KJ5HST" \
    --description "Hamlib-compatible emulator for Yaesu FTX-1" \
    --mac-package-name "${APP_NAME}" \
    --java-options "-Xmx256m" \
    --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED"

echo ""
echo "App bundle created: ${OUTPUT_DIR}/${APP_NAME}.app"
echo ""

# Check if we should sign
if [ "$1" == "--sign" ]; then
    if [ -z "$2" ]; then
        echo "Usage: $0 --sign 'Developer ID Application: Your Name (TEAMID)'"
        exit 1
    fi
    SIGNING_ID="$2"
    echo "Signing with: ${SIGNING_ID}"
    codesign --force --deep --sign "${SIGNING_ID}" "${OUTPUT_DIR}/${APP_NAME}.app"
    echo "Signed successfully."
    echo ""
    echo "To notarize, run:"
    echo "  xcrun notarytool submit ${OUTPUT_DIR}/${APP_NAME}.app --apple-id YOUR_EMAIL --team-id TEAMID --wait"
    echo "  xcrun stapler staple ${OUTPUT_DIR}/${APP_NAME}.app"
else
    echo "To sign (requires Apple Developer account):"
    echo "  $0 --sign 'Developer ID Application: Your Name (TEAMID)'"
    echo ""
    echo "To run without signing, users can:"
    echo "  Right-click the app -> Open"
    echo "  Or: xattr -d com.apple.quarantine ${OUTPUT_DIR}/${APP_NAME}.app"
fi

echo ""
echo "To create a DMG for distribution:"
echo "  hdiutil create -volname ${APP_NAME} -srcfolder ${OUTPUT_DIR}/${APP_NAME}.app -ov -format UDZO ${OUTPUT_DIR}/${APP_NAME}-${VERSION}.dmg"
