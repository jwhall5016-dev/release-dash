@echo off
REM Release Dashboard - Build and Run Script with Debugger Support (Windows)
REM Usage:
REM   run.bat           - Build and run normally
REM   run.bat debug     - Build and run with debugger on port 5005
REM   run.bat --skip-build - Skip building, just run

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set FRONTEND_DIR=%SCRIPT_DIR%frontend
set BACKEND_DIR=%SCRIPT_DIR%backend
set DEBUG_MODE=false
set SKIP_BUILD=false

REM Parse arguments
for %%a in (%*) do (
    if "%%a"=="debug" set DEBUG_MODE=true
    if "%%a"=="--debug" set DEBUG_MODE=true
    if "%%a"=="-d" set DEBUG_MODE=true
    if "%%a"=="--skip-build" set SKIP_BUILD=true
    if "%%a"=="-s" set SKIP_BUILD=true
)

echo ==========================================
echo Release Dashboard - Build ^& Run Script
echo ==========================================

REM Build frontend
if "%SKIP_BUILD%"=="false" (
    echo.
    echo Building frontend...
    cd /d "%FRONTEND_DIR%"
    call npm run build
    
    echo.
    echo Copying frontend to backend static resources...
    cd /d "%SCRIPT_DIR%"
    if exist "%BACKEND_DIR%\src\main\resources\static" rmdir /s /q "%BACKEND_DIR%\src\main\resources\static"
    mkdir "%BACKEND_DIR%\src\main\resources\static"
    xcopy /E /I /Y "%FRONTEND_DIR%\dist\*" "%BACKEND_DIR%\src\main\resources\static\"
    echo Frontend files copied
    
    echo.
    echo Building backend...
    cd /d "%BACKEND_DIR%"
    call mvn clean package -DskipTests
    echo Backend built successfully
) else (
    echo Skipping build (using existing artifacts)
)

REM Kill any existing process on port 8080
echo.
echo Cleaning up existing processes...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
)

REM Kill any existing process on debug port 5005 if in debug mode
if "%DEBUG_MODE%"=="true" (
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5005 ^| findstr LISTENING') do (
        taskkill /F /PID %%a >nul 2>&1
    )
)

REM Run the application
echo.
if "%DEBUG_MODE%"=="true" (
    echo Starting application with DEBUGGER enabled...
    echo    Debug port: 5005
    echo    Application port: 8080
    echo.
    echo    To attach debugger:
    echo    - IntelliJ IDEA: Run -^> Attach to Process -^> Remote JVM Debug
    echo    - VS Code: Use Java Debug extension with port 5005
    echo.
    cd /d "%BACKEND_DIR%"
    call mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
) else (
    echo Starting application...
    echo    Application port: 8080
    echo.
    cd /d "%BACKEND_DIR%"
    call mvn spring-boot:run
)
