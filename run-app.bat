@echo off
setlocal
title Couple Gallery App Launcher
echo ==========================================
echo   Starting Couple App Gallery (No Studio)
echo ==========================================

set "EMULATOR=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"

REM 1. Check if emulator is already running
"%ADB%" devices | findstr /R "emulator-[0-9]*.*device$" >nul
if %errorlevel% neq 0 (
    echo [1/3] Starting Pixel_8 emulator...
    start "" "%EMULATOR%" -avd Pixel_8 -gpu host
    echo [2/3] Waiting for emulator to finish booting...
    "%ADB%" wait-for-device
    :wait_boot
    for /f "tokens=*" %%a in ('"%ADB%" shell getprop sys.boot_completed 2^>nul') do set BOOT=%%a
    if not "%BOOT%"=="1" (
        timeout /t 2 /nobreak >nul
        goto wait_boot
    )
    echo Emulator is ready!
) else (
    echo [1/3] Pixel_8 emulator is already running!
)

REM 2. Install latest APK
echo [2/3] Installing latest app build...
"%ADB%" install -r "%APK%"

REM 3. Launch the app
echo [3/3] Opening Couple Gallery App...
"%ADB%" shell am start -n com.example.coupleappgallery/.MainActivity

echo.
echo ==========================================
echo   App is running on your Pixel 8!
echo ==========================================
timeout /t 3 >nul
