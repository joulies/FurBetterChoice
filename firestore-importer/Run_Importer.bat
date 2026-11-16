@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================================
echo   FurBetterChoice - One-Click Firestore Product Importer
echo ============================================================
echo.

where python >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python is not installed or not in PATH.
    echo Please install Python 3.10+ from https://www.python.org/downloads/
    echo Make sure to check "Add Python to PATH" during installation.
    echo.
    pause
    exit /b 1
)

set "PY_CMD=python"
python -c "import openpyxl" >nul 2>&1
if errorlevel 1 (
    if not exist ".venv\Scripts\python.exe" (
        echo [1/2] Setting up local Python virtual environment...
        python -m venv ".venv"
        if errorlevel 1 (
            echo [ERROR] Failed to create virtual environment.
            pause
            exit /b 1
        )
    )
    set "PY_CMD=.venv\Scripts\python.exe"
    "%PY_CMD%" -c "import openpyxl" >nul 2>&1
    if errorlevel 1 (
        echo [1/2] Installing openpyxl...
        "%PY_CMD%" -m pip install --upgrade pip >nul 2>&1
        "%PY_CMD%" -m pip install -r requirements.txt
        if errorlevel 1 (
            echo [ERROR] Failed to install openpyxl. Check your internet connection.
            pause
            exit /b 1
        )
    )
)

echo Running Firestore importer...
if "%~1"=="" (
    "%PY_CMD%" import_products.py
) else (
    "%PY_CMD%" import_products.py --excel "%~1"
)

set "EXIT_CODE=%ERRORLEVEL%"
echo.
if "%EXIT_CODE%"=="0" (
    echo Done! You can close this window.
) else (
    echo Importer exited with error code %EXIT_CODE%. See messages above.
)
pause
exit /b %EXIT_CODE%
