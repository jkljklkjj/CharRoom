#!/usr/bin/env bash
set -e

# Simple macOS codesign helper.
# Requires environment variable MACOS_IDENTITY (e.g. "Developer ID Application: Your Name (TEAMID)")
# Usage: ./sign-macos.sh path/to/MyApp.app [path/to/another.app]

if [ -z "$MACOS_IDENTITY" ]; then
  echo "MACOS_IDENTITY environment variable is required (example: 'Developer ID Application: Your Name (TEAMID)')"
  exit 1
fi

if [ $# -lt 1 ]; then
  echo "Usage: $0 path/to/App.app"
  exit 1
fi

for target in "$@"; do
  if [ ! -e "$target" ]; then
    echo "Target not found: $target"
    continue
  fi
  echo "Signing $target with identity: $MACOS_IDENTITY"
  # Use runtime options for hardened runtime compatibility
  codesign --timestamp --options runtime --sign "$MACOS_IDENTITY" --deep "$target"
  echo "Signed $target"
done
