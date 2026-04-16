param(
  [Parameter(Mandatory=$true)] [string]$token,
  [Parameter(Mandatory=$true)] [int]$workflow_id,
  [string]$repo = 'jkljklkjj/CharRoom'
)

$uri = "https://api.github.com/repos/$repo/actions/workflows/$workflow_id/runs?per_page=5"
try {
  $r = Invoke-RestMethod -Uri $uri -Headers @{ Authorization = "token $token"; Accept = 'application/vnd.github.v3+json' }
  if ($r.workflow_runs -and $r.workflow_runs.Count -gt 0) {
    $latest = $r.workflow_runs[0]
    $latest | Select-Object id, html_url, status, conclusion, created_at | ConvertTo-Json -Depth 3
  } else {
    Write-Host 'No runs found for workflow.'
  }
} catch {
  Write-Host 'Failed to query workflow runs:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
