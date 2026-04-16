Signing and Notarization (examples)

This document explains how to provide signing credentials to CI and how to sign/notarize produced installers.

Secrets used in GitHub Actions
- `WINDOWS_PFX` (base64): base64-encoded .pfx file for Windows code signing.
- `WINDOWS_PFX_PASSWORD`: password for the PFX.
- `MACOS_IDENTITY` (optional): code signing identity string for `codesign`, e.g. "Developer ID Application: Your Name (TEAMID)".
- `APPLE_API_KEY_BASE64` (optional): base64-encoded App Store Connect API key (.p8) content for notarization.
- `APPLE_KEY_ID` (optional): App Store Connect Key ID (e.g. ABCDE12345).
- `APPLE_ISSUER_ID` (optional): Apple Issuer ID (Team ID).

How to generate base64 PFX (Linux/macOS):
```bash
base64 -w 0 mycert.pfx > mycert.pfx.b64
# copy contents and add to GitHub secret WINDOWS_PFX
```
On macOS without -w:
```bash
base64 mycert.pfx | tr -d '\n' > mycert.pfx.b64
```
On Windows (PowerShell):
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\path\to\mycert.pfx')) | Out-File -Encoding ASCII mycert.pfx.b64
```

Recommended CI workflow
- Use `.github/workflows/build-installers.yml` (added) which builds on `windows-latest` and `macos-latest`.
- Set the necessary repository secrets via GitHub Settings → Secrets.
- Trigger workflow via `workflow_dispatch` or by pushing a tag `v*`.

Local signing (macOS)
- Build DMG locally on macOS.
- Run `./scripts/sign-macos.sh /path/to/MyApp.app` (set `MACOS_IDENTITY` env var first).
- Notarize: `APPLE_API_KEY_BASE64` + `APPLE_KEY_ID` + `APPLE_ISSUER_ID` required; run `./scripts/notarize-macos.sh /path/to/MyApp.dmg`.

Local signing (Windows)
- Run `scripts/sign-windows.ps1 -Files "build\\compose\\binaries\\**\\*.msi"` with env `WINDOWS_PFX` (base64) and `WINDOWS_PFX_PASSWORD`.

Notes and caveats
- macOS DMG notarization requires Apple Developer credentials and must be run on macOS with Xcode tools installed.
- Windows MSI signing requires a PFX code signing certificate. The script imports the PFX into current user cert store for use by signtool.
- CI secrets must be protected; do NOT commit raw certificates into the repository.

If you want, I can:
- Add a GitHub Actions workflow job to automatically create a GitHub Release and attach installer artifacts.
- Generate placeholder icons and commit them under `packaging/icons/` (currently README exists).