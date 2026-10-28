<#
Signs and verifies final Windows release artifacts. Requires WINDOWS_PFX (base64)
and WINDOWS_PFX_PASSWORD. Uses the PFX directly without importing a certificate.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
    [string[]]$Files,
    [string]$SignToolPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $env:WINDOWS_PFX -or -not $env:WINDOWS_PFX_PASSWORD) {
    throw 'WINDOWS_PFX and WINDOWS_PFX_PASSWORD must both be provided.'
}

# Resolve every artifact before signing anything. Missing globs must fail the release.
$artifacts = @(
    foreach ($pattern in $Files) {
        $matches = @(Resolve-Path -Path $pattern -ErrorAction Stop)
        if ($matches.Count -eq 0) { throw "No installer matched: $pattern" }
        foreach ($match in $matches) {
            $item = Get-Item -LiteralPath $match.ProviderPath
            if ($item.PSIsContainer -or $item.Extension -notin @('.msi', '.exe')) {
                throw "Expected an MSI or EXE: $($item.Name)"
            }
            $item.FullName
        }
    }
) | Sort-Object -Unique
if (-not $artifacts) { throw 'No installers provided.' }

if ($SignToolPath) {
    $signer = (Get-Command $SignToolPath -CommandType Application -ErrorAction Stop).Source
} else {
    $onPath = Get-Command signtool.exe -CommandType Application -ErrorAction SilentlyContinue
    if ($onPath) {
        $signer = $onPath.Source
    } else {
        # SDK tools live under a versioned directory on current Windows runners.
        $sdkRoot = if (${env:ProgramFiles(x86)}) { Join-Path ${env:ProgramFiles(x86)} 'Windows Kits/10/bin' } else { $null }
        $candidates = if ($sdkRoot -and (Test-Path -LiteralPath $sdkRoot)) {
            Get-ChildItem -LiteralPath $sdkRoot -Directory |
                Where-Object { $_.Name -match '^\d+\.\d+\.\d+\.\d+$' } |
                Sort-Object { [version]$_.Name } -Descending |
                ForEach-Object { Join-Path $_.FullName 'x64/signtool.exe' } |
                Where-Object { Test-Path -LiteralPath $_ -PathType Leaf }
        }
        $signer = @($candidates) | Select-Object -First 1
        if (-not $signer) { throw 'signtool.exe not found; install Windows SDK or supply -SignToolPath.' }
    }
}

$certificate = Join-Path ([IO.Path]::GetTempPath()) ("chatlite-sign-{0}.pfx" -f [Guid]::NewGuid().ToString('N'))
try {
    [IO.File]::WriteAllBytes($certificate, [Convert]::FromBase64String($env:WINDOWS_PFX))
    foreach ($artifact in $artifacts) {
        Write-Host "Signing: $([IO.Path]::GetFileName($artifact))"
        & $signer sign /fd SHA256 /a /f $certificate /p $env:WINDOWS_PFX_PASSWORD /tr http://timestamp.digicert.com /td SHA256 $artifact
        if ($LASTEXITCODE -ne 0) { throw "signtool sign failed (exit $LASTEXITCODE)." }
        & $signer verify /pa /all $artifact
        if ($LASTEXITCODE -ne 0) { throw "signtool verify failed (exit $LASTEXITCODE)." }
    }
} finally {
    if (Test-Path -LiteralPath $certificate) { Remove-Item -LiteralPath $certificate -Force }
}
