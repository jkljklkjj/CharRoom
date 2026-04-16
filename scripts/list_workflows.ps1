param(
  [Parameter(Mandatory=$true)] [string]$token,
  [string]$repo = 'jkljklkjj/CharRoom'
)

$uri = "https://api.github.com/repos/$repo/actions/workflows"
try {
  $r = Invoke-RestMethod -Uri $uri -Headers @{ Authorization = "token $token"; Accept = 'application/vnd.github.v3+json' }
  $r.workflows | Select-Object id, name, path, state, html_url | ConvertTo-Json -Depth 5
} catch {
  Write-Host 'Failed to list workflows:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
