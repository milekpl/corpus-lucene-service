@echo off
REM Corpus Lucene Service - Stop Script (Windows)

setlocal

set PORT=8081

echo ============================================
echo   Stopping Corpus Lucene Service
echo ============================================
echo.

REM Find process using the port
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%PORT%') do (
    set PID=%%a
    goto :kill_process
)

echo No service found running on port %PORT%.
goto :check_by_name

:kill_process
echo Found process %PID% on port %PORT%.
taskkill /PID %PID% /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo Service stopped.
) else (
    echo Could not stop process.
)

:check_by_name
REM Also try to find by process name
echo.
echo Looking for corpus-lucene-service processes...
tasklist /FI "IMAGENAME eq java.exe" /FO CSV 2>nul | findstr /c:"corpus-lucene" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo Found corpus-lucene-service running.
    echo Use Task Manager to stop it if needed.
)

endlocal
pause
