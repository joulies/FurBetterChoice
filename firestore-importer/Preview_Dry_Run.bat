@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo   FurBetterChoice - Dry-Run Preview ^(No Firestore Writes^)
echo ============================================================
echo.

where python >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python is not installed or not in PATH.
    pause
    exit /b 1
)

set "PY_CMD=python"
if exist ".venv\Scripts\python.exe" (
    set "PY_CMD=.venv\Scripts\python.exe"
) else (
    python -c "import openpyxl" >nul 2>&1
    if errorlevel 1 (
        echo Setting up local virtual environment for preview...
        python -m venv ".venv"
        ".venv\Scripts\python.exe" -m pip install -r requirements.txt
        set "PY_CMD=.venv\Scripts\python.exe"
    )
)

if "%~1"=="" (
    "%PY_CMD%" import_products.py --dry-run
) else (
    "%PY_CMD%" import_products.py --dry-run --excel "%~1"
)

echo.
pause
