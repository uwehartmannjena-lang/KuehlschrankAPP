$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$launcher = Join-Path $projectDir "start-auto-coder.cmd"

if (-not (Test-Path -LiteralPath $launcher)) {
    Write-Host "start-auto-coder.cmd wurde nicht gefunden: $launcher"
    exit 1
}

Remove-Item Alias:start -Force -ErrorAction SilentlyContinue

Set-Item -Path Function:\global:start -Value {
    & "C:\KuehlschrankApp\start-auto-coder.cmd"
}

$profileDir = Split-Path -Parent $PROFILE
if (-not (Test-Path -LiteralPath $profileDir)) {
    New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
}

$markerStart = "# KuehlschrankApp Auto-Coder Start"
$markerEnd = "# Ende KuehlschrankApp Auto-Coder Start"
$profileBlock = @"

$markerStart
Remove-Item Alias:start -Force -ErrorAction SilentlyContinue
function global:start {
    & "C:\KuehlschrankApp\start-auto-coder.cmd"
}
$markerEnd
"@

$profileContent = ""
if (Test-Path -LiteralPath $PROFILE) {
    $profileContent = Get-Content -LiteralPath $PROFILE -Raw
}

$escapedStart = [regex]::Escape($markerStart)
$escapedEnd = [regex]::Escape($markerEnd)
$pattern = "(?s)\r?\n?$escapedStart.*?$escapedEnd"
$profileContent = [regex]::Replace($profileContent, $pattern, "")
Set-Content -LiteralPath $PROFILE -Value ($profileContent.TrimEnd() + $profileBlock) -Encoding UTF8

Write-Host "Fertig. In diesem PowerShell-Fenster startet jetzt 'start' den Auto-Coder."
Write-Host "Auch neue PowerShell-Fenster kennen den Befehl jetzt."
