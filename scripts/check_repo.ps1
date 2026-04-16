param(
  [Parameter(Mandatory=$true)] [string]$token,
  [string]$repo = 'jkljklkjj/CharRoom'
)

$api = "https://api.github.com/repos/$repo"
try {
  $r = Invoke-RestMethod -Uri $api -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" }
  Write-Host "default_branch: $($r.default_branch)"
} catch {
  Write-Host 'Repo query failed:'
  if ($_.Exception.Response) {
    $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
    Write-Host $sr.ReadToEnd()
  } else { Write-Host $_.Exception.Message }
  exit 1
}

# list branches
$api_branches = "https://api.github.com/repos/$repo/branches?per_page=100"
try {
  $b = Invoke-RestMethod -Uri $api_branches -Headers @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json" }
  Write-Host "Branches:" 
  $b | ForEach-Object { Write-Host $_.name }
} catch {
  Write-Host 'Branches query failed:'
  if ($_.Exception.Response) {
    $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
    Write-Host $sr.ReadToEnd()
  } else { Write-Host $_.Exception.Message }
  exit 1
}
