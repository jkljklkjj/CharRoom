param(
  [Parameter(Mandatory=$true)] [string]$token,
  [string]$repo = 'jkljklkjj/CharRoom'
)

$uri = "https://api.github.com/repos/$repo"
try {
  $r = Invoke-RestMethod -Uri $uri -Headers @{ Authorization = "token $token"; Accept = 'application/vnd.github.v3+json' }
  $r | Select-Object name, full_name, default_branch, private, created_at, updated_at | ConvertTo-Json -Depth 5
} catch {
  Write-Host 'Failed to fetch repo info:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
