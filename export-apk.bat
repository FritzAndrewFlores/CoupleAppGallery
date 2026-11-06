@echo off
setlocal
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo ==========================================
echo   Building Couple App APK (v1.0.1)...
echo ==========================================
call gradlew.bat assembleDebug --console=plain
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Build failed! Check errors above.
    pause
    exit /b %ERRORLEVEL%
)

set "SOURCE_APK=app\build\outputs\apk\debug\app-debug.apk"
set "TARGET_APK=%USERPROFILE%\Desktop\CoupleApp-v1.0.1.apk"

copy /Y "%SOURCE_APK%" "%TARGET_APK%" >nul
if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================================
    echo   SUCCESS! Downloadable APK created:
    echo   %TARGET_APK%
    echo ========================================================
) else (
    echo [ERROR] Could not copy APK to Desktop.
)
pause
