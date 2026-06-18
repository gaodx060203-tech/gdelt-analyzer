@echo off
chcp 65001 >nul
title GDELT Analyzer
echo.
echo ================================================
echo   GDELT Analyzer
echo   Global Geopolitical Situation Awareness System
echo ================================================
echo.
echo [INFO] Building project with Maven...
call .\mvnw.cmd package -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)
echo [INFO] Build successful. Starting application...
echo.
java -jar target\gdelt-analyzer-1.0.0.jar
pause
