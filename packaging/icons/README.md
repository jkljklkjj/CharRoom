Place platform icons here before building installers.

Recommended files and sizes:
- app.ico  — Windows icon (include 256x256 PNG resources inside .ico)
- app.icns — macOS icon (use iconutil or an .icns generator)
- app.png  — Linux (256x256)

Quick icon generation hints:
- From a single PNG (`src.png`):
  - macOS (.icns):
    - macOS: `iconutil -c icns App.iconset` after creating an `App.iconset` with multiple sizes.
    - Or use `png2icns app.icns src.png` if installed.
  - Windows (.ico):
    - Use ImageMagick: `convert src.png -define icon:auto-resize=256,128,64,48,32,16 app.ico`.

If you don't provide icons, the default platform icon will be used (jpackage default).