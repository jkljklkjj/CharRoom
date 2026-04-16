param(
  [Parameter(Mandatory=$true)] [string]$token,
  [Parameter(Mandatory=$true)] [string]$repo,
  [Parameter(Mandatory=$true)] [long]$jobId
)

$api = "https://api.github.com/repos/$repo/actions/jobs/$jobId/logs"
$outZip = Join-Path $env:TEMP "job_$jobId-logs.zip"
$dest = Join-Path $env:TEMP "job_$jobId-logs"

try {
  Invoke-WebRequest -Uri $api -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" } -OutFile $outZip -UseBasicParsing
  if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
  Expand-Archive -Path $outZip -DestinationPath $dest -Force
  Write-Host "Logs extracted to: $dest"
  Get-ChildItem -Path $dest -Recurse -Filter *.txt | ForEach-Object {
    Write-Host "--- File: $($_.FullName) ---"
    Get-Content -Path $_.FullName -Tail 300 | ForEach-Object { Write-Host $_ }
  }
} catch {
  Write-Host "Failed to download or extract logs:"; if ($_.Exception.Response) { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } else { Write-Host $_.Exception.Message }
  exit 1
}
