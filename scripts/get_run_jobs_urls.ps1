param(
  [Parameter(Mandatory=$true)] [string]$token,
  [Parameter(Mandatory=$true)] [string]$repo,
  [Parameter(Mandatory=$true)] [long]$runId
)

$api = "https://api.github.com/repos/$repo/actions/runs/$runId/jobs"
try {
  $jobs = Invoke-RestMethod -Uri $api -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" }
} catch {
  Write-Host 'Failed to fetch jobs:'
  if ($_.Exception.Response) { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } else { Write-Host $_.Exception.Message }
  exit 1
}

foreach ($job in $jobs.jobs) {
  Write-Host "Job: $($job.name) id:$($job.id) logs_url: $($job.logs_url)"
}
