# Push GEMINI_API_KEY from repo .env to phone (debug builds only).
$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$EnvFile = Join-Path $RepoRoot ".env"
$Pkg = "com.google.mediapipe.examples.llminference"

if (-not (Test-Path $EnvFile)) {
    Write-Error ".env not found at $EnvFile"
}

$key = ""
Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*GEMINI_API_KEY\s*=\s*(.+)\s*$') {
        $key = $Matches[1].Trim()
    }
}
if (-not $key) {
    Write-Error "GEMINI_API_KEY missing in .env"
}

$key | adb shell "run-as $Pkg tee files/gemini_key_import" | Out-Null

adb shell am force-stop $Pkg
adb shell am start -n "$Pkg/.MainActivity"
Write-Host "Key pushed - app restarted. Check logcat: GeminiKeyImporter"
