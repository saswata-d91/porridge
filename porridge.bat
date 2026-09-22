@echo off
setlocal enabledelayedexpansion

set REQUIRED_JAVA_VERSION=17
set JRE_DIR=%USERPROFILE%\.porridge\jre
set JAVA_CMD=java

:: 1. Check if java is installed
java -version 2>nul
if %errorlevel% neq 0 (
    echo [SYSTEM] Java not found in PATH.
    goto :download_java
)

:: Extract version
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VER=%%g
)
set JAVA_VER=%JAVA_VER:"=%
for /f "tokens=1 delims=." %%a in ("%JAVA_VER%") do set MAJOR=%%a
if "%MAJOR%"=="1" (
    for /f "tokens=2 delims=." %%a in ("%JAVA_VER%") do set MAJOR=%%a
)

if %MAJOR% geq %REQUIRED_JAVA_VERSION% (
    goto :run_jar
) else (
    echo [SYSTEM] Java version %MAJOR% is less than required %REQUIRED_JAVA_VERSION%.
    goto :download_java
)

:download_java
set JAVA_CMD=%JRE_DIR%\bin\java.exe
if exist "%JAVA_CMD%" goto :run_jar

echo [SYSTEM] Downloading JRE 17...
if not exist "%JRE_DIR%" mkdir "%JRE_DIR%"
set DOWNLOAD_URL=https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse
set ZIP_FILE=%JRE_DIR%\jre.zip

powershell -Command "Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%ZIP_FILE%'"
echo [SYSTEM] Extracting JRE...
powershell -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%JRE_DIR%\temp' -Force"

:: Adoptium extracts to a subfolder, move it up
for /d %%I in ("%JRE_DIR%\temp\*") do (
    xcopy /s /e /q /y "%%I\*" "%JRE_DIR%\"
)
rmdir /s /q "%JRE_DIR%\temp"
del "%ZIP_FILE%"
goto :run_jar

:run_jar
set JAR_FILE=porridge-core\target\claude-code-java-harness-0.0.1-SNAPSHOT.jar

if not exist "%JAR_FILE%" (
    echo [SYSTEM] Building Porridge...
    call mvn clean package -DskipTests
    if %errorlevel% neq 0 (
        echo [ERROR] Maven build failed.
        exit /b 1
    )
)

"%JAVA_CMD%" -jar "%JAR_FILE%" %*
