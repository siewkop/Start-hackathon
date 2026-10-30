param([int]$Port = 8000, [switch]$Lan)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $projectRoot 'backend'
$venvPython = Join-Path $projectRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $venvPython)) {
    python -m venv (Join-Path $projectRoot '.venv')
    if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie utworzyc srodowiska Python.' }
}
& $venvPython -m pip install -r (Join-Path $backendPath 'requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie zainstalowac zaleznosci backendu.' }
$bindAddress = if ($Lan) { '0.0.0.0' } else { '127.0.0.1' }
Push-Location $backendPath
try { & $venvPython -m uvicorn app.main:app --host $bindAddress --port $Port --workers 1 }
finally { Pop-Location }
