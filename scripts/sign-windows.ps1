<#
PowerShell helper to sign Windows installer files using a base64-encoded PFX provided in env var WINDOWS_PFX.
Usage example in CI (secrets stored in GitHub):
  $env:WINDOWS_PFX = '<base64 content>'
  $env:WINDOWS_PFX_PASSWORD = '<pfx password>'
  ./sign-windows.ps1 -Files build\compose\binaries\**\*.msi
#>
param(
  [Parameter(Mandatory=$true, ValueFromRemainingArguments=$true)]
  [String[]]$Files
)

if (-not $env:WINDOWS_PFX -or -not $env:WINDOWS_PFX_PASSWORD) {
  Write-Error "Environment variables WINDOWS_PFX and WINDOWS_PFX_PASSWORD are required"
  exit 1
}

try {
  $pfxPath = Join-Path $env:TEMP 'codesign.pfx'
  [IO.File]::WriteAllBytes($pfxPath, [Convert]::FromBase64String($env:WINDOWS_PFX))
  $pw = $env:WINDOWS_PFX_PASSWORD
  certutil -f -p $pw -importpfx $pfxPath | Out-Null
  $signtool = 'C:\Program Files (x86)\Windows Kits\10\bin\x64\signtool.exe'
  foreach ($f in $Files) {
    $matches = Get-ChildItem -Path $f -Recurse -File -ErrorAction SilentlyContinue
    foreach ($m in $matches) {
      Write-Host "Signing: $($m.FullName)"
      & $signtool sign /fd SHA256 /a /f $pfxPath /p $pw /tr http://timestamp.digicert.com /td SHA256 $m.FullName
    }
  }
} catch {
  Write-Error "Signing failed: $_"
  exit 1
}
