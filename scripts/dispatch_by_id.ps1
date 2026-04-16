param(
  [Parameter(Mandatory=$true)] [string]$token,
  [Parameter(Mandatory=$true)] [int]$workflow_id,
  [string]$repo = 'jkljklkjj/CharRoom',
  [string]$ref = 'master'
)

$uri = "https://api.github.com/repos/$repo/actions/workflows/$workflow_id/dispatches"
$body = @{ ref = $ref } | ConvertTo-Json
try {
  Invoke-RestMethod -Uri $uri -Method Post -Headers @{ Authorization = "token $token"; Accept = 'application/vnd.github.v3+json' } -Body $body -ContentType 'application/json'
  Write-Host 'Workflow dispatch requested successfully.'
} catch {
  Write-Host 'Dispatch failed:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
