param([string]$Profile = (Join-Path $env:APPDATA 'ModrinthApp/profiles/NeoForge 1.21.1'))
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskVersion = (Get-Content -LiteralPath (Join-Path $taskRoot 'gradle.properties') | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
if ($taskVersion -ne '0.4.0') { throw 'This test profile expects WGE version 0.4.0.' }
$taskSource = Join-Path $taskRoot 'release/worldgen-editor-neoforge-0.4.0.jar'
if (!(Test-Path -LiteralPath $taskSource -PathType Leaf)) { throw 'Run the checked release build first.' }
$taskMods = Join-Path (Resolve-Path -LiteralPath $Profile).Path 'mods'
if (!(Test-Path -LiteralPath $taskMods -PathType Container)) { throw 'The profile mods directory does not exist.' }
$taskTarget = Join-Path $taskMods 'worldgen-editor-neoforge-0.4.0.jar'
$taskBackups = Join-Path $taskRoot ('build/test-profile-backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
if (Test-Path -LiteralPath $taskTarget) {
    New-Item -ItemType Directory -Path $taskBackups -Force | Out-Null
    Copy-Item -LiteralPath $taskTarget -Destination (Join-Path $taskBackups 'worldgen-editor-neoforge-0.4.0.jar')
}
Copy-Item -LiteralPath $taskSource -Destination $taskTarget -Force
$taskExpected = (Get-FileHash -LiteralPath $taskSource -Algorithm SHA256).Hash
if ((Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash -ne $taskExpected) { throw 'Installed jar checksum mismatch.' }
Write-Output "Installed WGE 0.4.0: $taskTarget"
Write-Output "SHA256: $taskExpected"
