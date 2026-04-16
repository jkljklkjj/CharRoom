param(
  [Parameter(Mandatory=$true)] [string]$token,
  [string]$repo = 'jkljklkjj/CharRoom',
  [string]$path = '.github/workflows/build-installers.yml',
  [string]$ref = 'master'
)

$uri = "https://api.github.com/repos/$repo/contents/$path?ref=$ref"
try {
  $r = Invoke-RestMethod -Uri $uri -Headers @{ Authorization = "token $token"; Accept = 'application/vnd.github.v3+json' }
  $content = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($r.content))
  Write-Output $content
} catch {
  Write-Host 'Failed to fetch remote workflow file:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
