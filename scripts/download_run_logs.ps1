param(
  [Parameter(Mandatory=$true)] [string]$token,
  [Parameter(Mandatory=$true)] [string]$repo,
  [Parameter(Mandatory=$true)] [long]$runId
)

$api = "https://api.github.com/repos/$repo/actions/runs/$runId/logs"
$outZip = Join-Path $env:TEMP "run_$runId-logs.zip"
$dest = Join-Path $env:TEMP "run_$runId-logs"

try {
  Invoke-WebRequest -Uri $api -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" } -OutFile $outZip -UseBasicParsing
  if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
  Expand-Archive -Path $outZip -DestinationPath $dest -Force
  Write-Host "Run logs extracted to: $dest"
  Get-ChildItem -Path $dest -Recurse -Filter *.txt | ForEach-Object {
    Write-Host "--- File: $($_.FullName) ---"
    Get-Content -Path $_.FullName -Tail 300 | ForEach-Object { Write-Host $_ }
  }
} catch {
  Write-Host "Failed to download or extract run logs:"; if ($_.Exception.Response) { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } else { Write-Host $_.Exception.Message }
  exit 1
}
