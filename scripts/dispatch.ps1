param(
  [Parameter(Mandatory=$true)] [string]$token,
  [string]$repo = 'jkljklkjj/CharRoom',
  [string]$ref = 'main'
)

$api = "https://api.github.com/repos/$repo/actions/workflows/build-installers.yml/dispatches"
$body = @{ ref = $ref } | ConvertTo-Json

try {
  Invoke-RestMethod -Uri $api -Method Post -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" } -Body $body -ContentType 'application/json'
  Write-Host 'Workflow dispatch requested successfully.'
} catch {
  Write-Host 'Dispatch failed:'
  # Dump full error for troubleshooting
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}

# Query recent runs for this workflow
$api_runs = "https://api.github.com/repos/$repo/actions/workflows/build-installers.yml/runs?per_page=5"
try {
  $runs = Invoke-RestMethod -Uri $api_runs -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" }
  if ($runs.workflow_runs -and $runs.workflow_runs.Count -gt 0) {
    $latest = $runs.workflow_runs | Select-Object -First 1
    Write-Host "Latest run:"
    Write-Host ("id: " + $latest.id)
    Write-Host ("html_url: " + $latest.html_url)
    Write-Host ("status: " + $latest.status)
    Write-Host ("conclusion: " + $latest.conclusion)
    Write-Host ("created_at: " + $latest.created_at)
  } else {
    Write-Host 'No recent runs found for workflow.'
  }
} catch {
  Write-Host 'Failed to query runs:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
