@echo off
setlocal
set "GRADLE_VERSION=8.7"
set "ROOT_DIR=%~dp0"
set "CACHE_DIR=%ROOT_DIR%.gradle-local"
set "DIST_DIR=%CACHE_DIR%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%DIST_DIR%\bin\gradle.bat"
if exist "%GRADLE_BIN%" goto RUN
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
set "ARCHIVE=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
echo Downloading Gradle %GRADLE_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ARCHIVE%'"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ARCHIVE%' '%CACHE_DIR%'"
del /q "%ARCHIVE%"
:RUN
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
