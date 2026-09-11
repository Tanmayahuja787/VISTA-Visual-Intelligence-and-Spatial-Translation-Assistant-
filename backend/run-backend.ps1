$ErrorActionPreference = "Stop"

$python = "C:\Users\tanma\AppData\Local\Python\pythoncore-3.14-64\python.exe"
Set-Location $PSScriptRoot

if (-not (Test-Path ".env")) {
    Write-Error "Create backend\.env and set GEMINI_API_KEY before starting the backend."
}

& $python -m uvicorn main:app --host 0.0.0.0 --port 8000
