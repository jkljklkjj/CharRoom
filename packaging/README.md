Build native installers (Windows .msi and macOS .dmg)

Prerequisites
- JDK 17+ with `jpackage` available. On macOS and Linux, use the same JDK used to build (AdoptOpenJDK, Temurin, or Zulu with jpackage support).
- On Windows: WiX Toolset (for MSI generation). Install WiX and ensure `candle.exe`/`light.exe` are on PATH.
- On macOS: `codesign` provided by Xcode Command Line Tools (for signing). Notarization requires an Apple Developer account and additional steps.

Place icons (optional)
- Put `app.icns` (mac) and `app.ico` (windows) into `packaging/icons/`.

Build commands
- Build installers for the current host (run on the target OS):

```bash
# Linux / macOS
./gradlew buildInstallers

# Windows (PowerShell)
./gradlew.bat buildInstallers
```

Notes
- The Compose plugin uses `jpackage` under the hood. Building a macOS DMG must be run on macOS to get a DMG. Building an MSI should be run on Windows (WiX required).
- If you need cross-builds, consider using CI runners for each OS (GitHub Actions with macOS and Windows runners).

Signing and Notarization
- macOS: sign your app with an Apple Developer certificate (`codesign`) and optionally notarize with `altool` or `notarytool`.
- Windows: sign the MSI/EXE with `signtool` (requires a code signing certificate).

If you want, I can:
- 1) Add default `packaging/icons` placeholders and a minimal `LICENSE.txt` file.
- 2) Run `./gradlew buildInstallers` here (but note: building macOS DMG requires macOS, building MSI requires WiX on Windows).
- 3) Add CI GitHub Actions workflows for producing installers on each OS.