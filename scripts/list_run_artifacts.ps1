param(
  [Parameter(Mandatory=$true)] [string]$token,
  [Parameter(Mandatory=$true)] [string]$repo,
  [Parameter(Mandatory=$true)] [long]$runId
)

$uri = "https://api.github.com/repos/$repo/actions/runs/$runId/artifacts"
try {
  $r = Invoke-RestMethod -Uri $uri -Headers @{ Authorization = "token $token"; Accept = 'application/vnd.github.v3+json' }
  if ($r.total_count -gt 0) {
    $r.artifacts | Select-Object id, name, size_in_bytes, archive_download_url | ConvertTo-Json -Depth 5
  } else {
    Write-Host "No artifacts found for run $runId"
  }
} catch {
  Write-Host 'Failed to list artifacts:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
