@echo off
chcp 65001 >nul
title GDELT Analyzer
echo.
echo ================================================
echo   GDELT Analyzer
echo   Global Geopolitical Situation Awareness System
echo ================================================
echo.
if not exist target\gdelt-analyzer-1.0.0.jar (
    echo [ERROR] JAR not found. Please run 'run.bat' first to build.
    pause
    exit /b 1
)
echo [INFO] Starting application...
echo.
java -jar target\gdelt-analyzer-1.0.0.jar
pause
