#!/usr/bin/env bash
set -e

# Notarize macOS DMG or PKG using App Store Connect API key (recommended)
# Required env vars:
# - APPLE_API_KEY_BASE64: base64 content of the .p8 API key
# - APPLE_KEY_ID: key id (e.g. ABCDE12345)
# - APPLE_ISSUER_ID: issuer (team) id
# Usage: ./notarize-macos.sh path/to/your.dmg

if [ -z "$APPLE_API_KEY_BASE64" ] || [ -z "$APPLE_KEY_ID" ] || [ -z "$APPLE_ISSUER_ID" ]; then
  echo "Set APPLE_API_KEY_BASE64, APPLE_KEY_ID, APPLE_ISSUER_ID in environment"
  exit 1
fi

if [ $# -lt 1 ]; then
  echo "Usage: $0 path/to/file.dmg"
  exit 1
fi

# Decode API key
API_KEY_FILE="/tmp/appstore_authkey.p8"
echo "$APPLE_API_KEY_BASE64" | base64 --decode > "$API_KEY_FILE"

for f in "$@"; do
  if [ ! -f "$f" ]; then
    echo "Not found: $f"
    continue
  fi
  echo "Submitting $f for notarization"
  xcrun notarytool submit "$f" --key "$API_KEY_FILE" --key-id "$APPLE_KEY_ID" --issuer "$APPLE_ISSUER_ID" --wait
  echo "Stapling $f"
  xcrun stapler staple "$f" || true
  echo "Notarization complete for $f"
done

# Clean up
rm -f "$API_KEY_FILE"
