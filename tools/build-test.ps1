$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
    & './gradlew.bat' 'build' '--no-daemon'
    if ($LASTEXITCODE) { throw 'Build or verification failed; the test profile was not changed.' }
    & (Join-Path $PSScriptRoot 'deploy-test-build.ps1')
} finally { Pop-Location }
