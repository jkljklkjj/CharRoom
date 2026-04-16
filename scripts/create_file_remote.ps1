param(
  [Parameter(Mandatory=$true)] [string]$token,
  [string]$repo = 'jkljklkjj/CharRoom',
  [string]$branch = 'master',
  [string]$localPath = '.github/workflows/dispatch-test.yml',
  [string]$message = 'chore(ci): add dispatch-test workflow'
)

$fullLocal = Join-Path (Get-Location) $localPath
if (-not (Test-Path $fullLocal)) {
  Write-Host "Local file not found: $fullLocal"
  exit 1
}

$content = Get-Content -Raw -Path $fullLocal -Encoding UTF8
$base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($content))

$api = "https://api.github.com/repos/$repo/contents/$localPath"
$body = @{ message = $message; content = $base64; branch = $branch } | ConvertTo-Json

try {
  $create = Invoke-RestMethod -Uri $api -Method Put -Headers @{ Authorization = "token $token"; Accept = 'application/vnd.github.v3+json' } -Body $body -ContentType 'application/json'
  Write-Host "Created file. Commit url: $($create.content.html_url)"
} catch {
  Write-Host 'Failed to create remote file:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
