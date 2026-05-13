@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Build Forge mod and copy the resulting jar into this Minecraft instance's mods folder.
rem Usage: double-click this file, or run it from cmd inside mod_work\forge_project.

cd /d "%~dp0"

set "MODS_DIR=%~dp0..\..\mods"
set "BUILD_LIBS=%~dp0build\libs"

if not exist "%MODS_DIR%" (
    echo [ERROR] Khong tim thay thu muc mods: "%MODS_DIR%"
    exit /b 1
)

if not exist "gradlew.bat" (
    echo [ERROR] Khong tim thay gradlew.bat trong "%CD%".
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    if exist "%USERPROFILE%\.gradle\jdks\eclipse_adoptium-17-amd64-windows\jdk-17.0.19+10\bin\java.exe" (
        set "JAVA_HOME=%USERPROFILE%\.gradle\jdks\eclipse_adoptium-17-amd64-windows\jdk-17.0.19+10"
        set "PATH=!JAVA_HOME!\bin;!PATH!"
        echo [INFO] Dang dung JDK 17 tu Gradle cache: "!JAVA_HOME!"
    ) else (
        echo [ERROR] Khong tim thay Java trong PATH va JAVA_HOME chua duoc cau hinh dung.
        echo [ERROR] Hay cai Java/JDK 17, hoac set JAVA_HOME den thu muc JDK 17, roi chay lai script.
        exit /b 1
    )
)

echo [INFO] Dang build mod bang Gradle wrapper...
call gradlew.bat --no-daemon clean build
if errorlevel 1 (
    echo [ERROR] Build failed. Kiem tra log Gradle o phia tren.
    exit /b 1
)

set "JAR_FILE="
for /f "delims=" %%F in ('dir /b /a-d /o-d "%BUILD_LIBS%\*.jar" 2^>nul') do (
    echo %%F | findstr /i /c:"-sources" /c:"-javadoc" /c:"-dev" /c:"-slim" >nul
    if errorlevel 1 (
        if not defined JAR_FILE set "JAR_FILE=%BUILD_LIBS%\%%F"
    )
)

if not defined JAR_FILE (
    echo [ERROR] Build thanh cong nhung khong tim thay jar output trong "%BUILD_LIBS%".
    exit /b 1
)

for %%G in ("!JAR_FILE!") do set "JAR_NAME=%%~nxG"

echo [INFO] Dang copy "!JAR_FILE!" sang "%MODS_DIR%"...
copy /Y "!JAR_FILE!" "%MODS_DIR%\" >nul
if errorlevel 1 (
    echo [ERROR] Copy jar vao mods failed.
    exit /b 1
)

echo [OK] Da build va copy jar vao "%MODS_DIR%".
echo [OK] File: %MODS_DIR%\!JAR_NAME!
exit /b 0
