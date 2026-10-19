param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

$BaseUrl = $BaseUrl.TrimEnd("/")
$FixtureDir = Join-Path $PSScriptRoot "..\src\api\fixtures"

New-Item -ItemType Directory -Force -Path $FixtureDir | Out-Null

function Save-JsonResponse {
    param(
        [string]$Path,
        [string]$OutputFile
    )

    $Uri = "$BaseUrl$Path"
    Write-Host "GET $Uri"

    $Response = Invoke-RestMethod `
        -Method Get `
        -Uri $Uri `
        -TimeoutSec 30

    $Destination = Join-Path $FixtureDir $OutputFile

    $Response |
        ConvertTo-Json -Depth 30 |
        Set-Content -Path $Destination -Encoding utf8

    Write-Host "Saved $Destination"
}

Write-Host ""
Write-Host "Recording anonymous, read-only ReleasePilot responses."
Write-Host "No login, approval, rejection, retry, or publishing action will be performed."
Write-Host ""

# Public/anonymous draft summary response.
Save-JsonResponse `
    -Path "/api/release/drafts" `
    -OutputFile "drafts.json"

# Knowledge index status.
Save-JsonResponse `
    -Path "/api/knowledge/status" `
    -OutputFile "knowledge-status.json"

Write-Host ""
Write-Host "Initial fixture recording complete."
Write-Host "Review the saved JSON before using it in Demo mode."