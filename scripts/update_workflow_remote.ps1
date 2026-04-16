param(
  [Parameter(Mandatory=$true)] [string]$token,
  [string]$repo = 'jkljklkjj/CharRoom',
  [string]$branch = 'master',
  [string]$localPath = '.github/workflows/build-installers.yml'
)

$fullLocal = Join-Path (Get-Location) $localPath
if (-not (Test-Path $fullLocal)) {
  Write-Host "Local file not found: $fullLocal"
  exit 1
}

$content = Get-Content -Raw -Path $fullLocal -Encoding UTF8
$base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($content))

$api = "https://api.github.com/repos/$repo/contents/$localPath"

try {
  # get current file to obtain sha
  $resp = Invoke-RestMethod -Uri ($api + "?ref=$branch") -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" }
  $sha = $resp.sha
  Write-Host "Remote file SHA: $sha"
} catch {
  Write-Host "Failed to fetch remote file info. Error:"; if ($_.Exception.Response) { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } else { Write-Host $_.Exception.Message }
  exit 1
}

$body = @{ message = "chore(ci): make workflow dispatch-safe by avoiding secrets in if-expressions"; content = $base64; sha = $sha; branch = $branch } | ConvertTo-Json

try {
  $update = Invoke-RestMethod -Uri $api -Method Put -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" } -Body $body -ContentType 'application/json'
  Write-Host "Updated file. Commit url: $($update.content.html_url)"
} catch {
  Write-Host "Failed to update remote file:"; if ($_.Exception.Response) { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } else { Write-Host $_.Exception.Message }
  exit 1
}
