param(
  [Parameter(Mandatory=$true)] [string]$token,
  [Parameter(Mandatory=$true)] [string]$repo,
  [Parameter(Mandatory=$true)] [long]$runId,
  [int]$interval = 15,
  [int]$timeoutMinutes = 30
)

$end = (Get-Date).AddMinutes($timeoutMinutes)
$r = $null
while ((Get-Date) -lt $end) {
  try {
    $r = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/actions/runs/$runId" -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" }
  } catch {
    Write-Host "Fetch failed: $($_.Exception.Message)"
    Start-Sleep -Seconds $interval
    continue
  }
  Write-Host "status: $($r.status) conclusion: $($r.conclusion) created_at: $($r.created_at)"
  if ($r.status -eq 'completed') { break }
  Start-Sleep -Seconds $interval
}

if ($r -and $r.status -eq 'completed') {
  Write-Host "Completed: $($r.conclusion)"
  Write-Host "Run URL: $($r.html_url)"
  exit 0
} else {
  Write-Host "Timeout waiting for run to complete"
  exit 2
}
