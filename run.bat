@echo off
title GDELT Analyzer (Source Mode)
cd /d "%~dp0"

echo.
echo ==================================
echo   GDELT Analyzer - Source Mode
echo ==================================
echo.

REM 检查 JDK
where javac >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] JDK not found! Please install JDK 17+.
    pause & exit /b 1
)

REM 创建必要目录
if not exist "data" mkdir "data"
if not exist "data\downloads" mkdir "data\downloads"
if not exist "data\imports" mkdir "data\imports"
if not exist "out" mkdir "out"

echo [1/2] Compiling...
javac -encoding UTF-8 -cp "lib/*" -d out src/com/gdelt/**/*.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed!
    pause & exit /b 1
)

echo [2/2] Starting application...
echo.
java -Dfile.encoding=UTF-8 -cp "out;lib/*" com.gdelt.MainApp
pause
