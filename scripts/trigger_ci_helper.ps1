param(
  [string]$ref = "master"
)

if (Get-Command gh -ErrorAction SilentlyContinue) {
  Write-Host "Using gh CLI to trigger workflow"
  gh workflow run build-installers.yml --repo 'jkljklkjj/CharRoom' --ref $ref
  exit $LASTEXITCODE
}

if ($env:GITHUB_TOKEN) {
  Write-Host "Using GITHUB_TOKEN to call dispatch.ps1"
  .\scripts\dispatch.ps1 -token $env:GITHUB_TOKEN -ref $ref
  exit $LASTEXITCODE
}

Write-Host "No gh CLI and no GITHUB_TOKEN available. Provide a PAT and run: .\\scripts\\dispatch.ps1 -token <PAT> -ref $ref"
exit 2
