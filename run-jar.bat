@echo off
title GDELT Analyzer v3.0 (JAR Mode)
cd /d "%~dp0"

echo.
echo ==================================
echo   GDELT Analyzer v3.0
echo   java -jar gdelt-analyzer.jar
echo ==================================
echo.

if not exist "gdelt-analyzer.jar" (
    echo [ERROR] gdelt-analyzer.jar not found!
    pause & exit /b 1
)

if not exist "lib\sqlite-jdbc.jar" (
    echo [ERROR] lib\sqlite-jdbc.jar not found!
    pause & exit /b 1
)

if not exist "data" mkdir "data"
if not exist "data\imports" mkdir "data\imports"
if not exist "data\downloads" mkdir "data\downloads"

echo Starting GDELT Analyzer...
echo.
java -jar -Dfile.encoding=UTF-8 gdelt-analyzer.jar
pause
