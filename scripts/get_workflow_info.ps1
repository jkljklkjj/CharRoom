param(
  [Parameter(Mandatory=$true)] [string]$token,
  [Parameter(Mandatory=$true)] [int]$workflow_id,
  [string]$repo = 'jkljklkjj/CharRoom'
)

$uri = "https://api.github.com/repos/$repo/actions/workflows/$workflow_id"
try {
  $r = Invoke-RestMethod -Uri $uri -Headers @{ Authorization = "token $token"; Accept = 'application/vnd.github.v3+json' }
  $r | ConvertTo-Json -Depth 5
} catch {
  Write-Host 'Failed to get workflow info:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
