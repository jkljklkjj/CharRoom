param(
  [Parameter(Mandatory=$true)] [string]$token,
  [string]$repo = 'jkljklkjj/CharRoom',
  [string]$path = '.github/workflows/build-installers.yml',
  [int]$per_page = 5
)

$uri = "https://api.github.com/repos/$repo/commits?path=$path&per_page=$per_page"
try {
  $r = Invoke-RestMethod -Uri $uri -Headers @{ Authorization = "token $token"; Accept = 'application/vnd.github.v3+json' }
  $r | Select-Object sha, commit | ConvertTo-Json -Depth 5
} catch {
  Write-Host 'Failed to fetch commits for path:'
  try { $_ | Format-List * -Force | Out-String | Write-Host } catch { Write-Host $_.Exception.Message }
  if ($_.Exception.Response) {
    try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $sr.ReadToEnd() } catch { }
  }
  exit 1
}
