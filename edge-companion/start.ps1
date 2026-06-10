# Med Veda Edge Companion - start API + web app + dashboard
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

Write-Host "=== Med Veda Edge Companion ===" -ForegroundColor Cyan

try {
    $tags = Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -TimeoutSec 5
    $modelName = $tags.models[0].name
    Write-Host "[OK] Ollama - $modelName" -ForegroundColor Green
} catch {
    Write-Host '[FAIL] Start Ollama first (port 11434)' -ForegroundColor Red
    exit 1
}

if (Get-Command adb -ErrorAction SilentlyContinue) {
    $dev = & adb devices 2>&1 | Select-String "device$"
    if ($dev) {
        & adb reverse tcp:8787 tcp:8787 2>&1 | Out-Null
        Write-Host '[OK] adb reverse 8787 (phone: http://127.0.0.1:8787)' -ForegroundColor Green
    }
}

$venvPy = Join-Path $Root "..\.venv\Scripts\python.exe"
if (-not (Test-Path $venvPy)) { $venvPy = "python" }

$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $venvPy -m pip install -q -r requirements.txt 2>&1 | Out-Null
$ErrorActionPreference = $prevEap

$webDir = Join-Path $Root "web"
$distDir = Join-Path $webDir "dist"
if (-not (Test-Path $distDir)) {
    if (Get-Command npm -ErrorAction SilentlyContinue) {
        Write-Host "Building web app (first run)..." -ForegroundColor Cyan
        Push-Location $webDir
        npm install 2>&1 | Out-Null
        npm run build 2>&1 | Out-Null
        Pop-Location
        if (Test-Path $distDir) {
            Write-Host '[OK] Web app built' -ForegroundColor Green
        }
    } else {
        Write-Host '[WARN] npm not found — web UI unavailable until you run npm run build in edge-companion/web' -ForegroundColor Yellow
    }
}

Write-Host 'Web app: http://localhost:8787/patients' -ForegroundColor Cyan
& $venvPy server.py
