@echo off
rem Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
rem
rem Deploys the project's externals into external\ (not under source control), from externals.conf — each tool only
rem when externals.conf names it:
rem   external\jdk, maven, gradle, node    JDK_WINDOWS, MAVEN_WINDOWS, GRADLE, NODE_WINDOWS: extracted, their top directory stripped
rem   external\jdk-windows.zip             JDK_WINDOWS again, as its archive: the jlink that links the distributions' runtimes
rem   external\jmods-linux.tar.gz, jmods-windows.zip, jmods-macos.tar.gz   JMODS_*: the jmods each platform's runtime is linked from
rem   external\downloads\                  the archives as downloaded (kept)
rem Each external remembers the URL it was deployed from (external\.<name>.url): one externals.conf names at another
rem version is deployed again.
rem then the project's own extras: scripts\windows\setup.local.bat, when it has one, is called with this script's arguments.
rem
rem   scripts\windows\setup.bat            deploys what is missing, or deployed from another URL than externals.conf's
rem   scripts\windows\setup.bat --force    downloads and deploys everything again
rem
rem MIRROR=<url> in externals.conf, or in the environment, fetches <MIRROR>/<file name> instead of the URLs;
rem MIRROR_TOKEN (+ MIRROR_TOKEN_HEADER) or MIRROR_USER / MIRROR_PASSWORD authenticate there — from
rem externals.local.conf (not under source control) or the environment rather than externals.conf.
rem JTOOLS_DOWNLOADS=<dir> in the environment keeps the downloads there instead, shared by every project.
rem Needs curl and tar, both in Windows 10 (1803) and later.
rem
setlocal EnableDelayedExpansion
cd /d "%~dp0..\.."
rem (the project root)

set "CONF=externals.conf"
set "LOCAL_CONF=externals.local.conf"
set "EXT=external"
set "DL=%EXT%\downloads"
if defined JTOOLS_DOWNLOADS set "DL=%JTOOLS_DOWNLOADS%"
set "FORCE=0"
if /i "%~1"=="--force" set "FORCE=1"

rem Windows' own curl and tar: a Git or Cygwin tar earlier on the PATH cannot read a zip
set "CURL=%SystemRoot%\System32\curl.exe"
if not exist "%CURL%" set "CURL=curl"
set "TAR=%SystemRoot%\System32\tar.exe"
if not exist "%TAR%" set "TAR=tar"
for %%t in ("%CURL%" "%TAR%") do if not exist %%t where %%~t >nul 2>nul || (
  echo %%~t not found ^(Windows 10 1803 and later have it^): install it, then run setup again >&2
  exit /b 1
)

rem externals.local.conf then externals.conf: KEY=VALUE lines, # comments; what is set already (the environment,
rem then the local file) wins
if exist "%LOCAL_CONF%" for /f "usebackq eol=# tokens=1* delims==" %%a in ("%LOCAL_CONF%") do if not defined %%a set "%%a=%%b"
for /f "usebackq eol=# tokens=1* delims==" %%a in ("%CONF%") do if not defined %%a set "%%a=%%b"

rem the authentication of the mirror, as curl arguments (none for the public URLs)
set "AUTH="
if defined MIRROR (
  if defined MIRROR_TOKEN (
    if not defined MIRROR_TOKEN_HEADER set "MIRROR_TOKEN_HEADER=Authorization: Bearer"
    set "AUTH=-H "!MIRROR_TOKEN_HEADER! !MIRROR_TOKEN!""
  )
  if defined MIRROR_USER set "AUTH=!AUTH! -u "!MIRROR_USER!:!MIRROR_PASSWORD!""
)

if not exist "%EXT%" mkdir "%EXT%"
if not exist "%DL%" mkdir "%DL%"

if defined JDK_WINDOWS call :tool jdk JDK_WINDOWS bin\java.exe || exit /b 1
if defined MAVEN_WINDOWS call :tool maven MAVEN_WINDOWS bin\mvn.cmd || exit /b 1
if defined GRADLE call :tool gradle GRADLE bin\gradle.bat || exit /b 1
if defined NODE_WINDOWS call :tool node NODE_WINDOWS node.exe || exit /b 1
if defined JDK_WINDOWS call :archive jdk-windows.zip JDK_WINDOWS || exit /b 1
if defined JMODS_LINUX call :archive jmods-linux.tar.gz JMODS_LINUX || exit /b 1
if defined JMODS_WINDOWS call :archive jmods-windows.zip JMODS_WINDOWS || exit /b 1
if defined JMODS_MACOS call :archive jmods-macos.tar.gz JMODS_MACOS || exit /b 1
rem a JDK up to 23 carries its jmods: its archive, for a platform with no JMODS_*
if defined JDK_LINUX if not defined JMODS_LINUX call :archive jdk-linux.tar.gz JDK_LINUX || exit /b 1
if defined JDK_MACOS if not defined JMODS_MACOS call :archive jdk-macos.tar.gz JDK_MACOS || exit /b 1
if exist scripts\windows\setup.local.bat call scripts\windows\setup.local.bat %* || exit /b 1

echo == external\ ready:
"%EXT%\jdk\bin\java.exe" -version 2>&1 | findstr /i "version"
set "JAVA_HOME=%CD%\%EXT%\jdk"
call "%EXT%\maven\bin\mvn.cmd" -v 2>nul | findstr /i "Apache Maven"
if exist "%EXT%\gradle\bin\gradle.bat" echo Gradle: %EXT%\gradle
if exist "%EXT%\node\node.exe" for /f %%v in ('"%EXT%\node\node.exe" --version') do echo Node.js %%v
exit /b 0

rem ---- a tool: extracted into external\<name>, the archive's top-level directory stripped; present when its
rem      launcher (%3) is — a failed deployment leaves none — and it came from the URL externals.conf names
:tool
set "DIR=%EXT%\%~1"
call :stamped %~1 %~2
if exist "%DIR%\%~3" if "%FORCE%"=="0" if "!STAMPED!"=="1" (
  echo == %DIR% present
  exit /b 0
)
call :fetch %~2 || exit /b 1
if exist "%DIR%" rmdir /s /q "%DIR%"
mkdir "%DIR%"
"%TAR%" -xf "!FILE!" -C "%DIR%" --strip-components 1 || exit /b 1
>"%EXT%\.%~1.url" echo !%~2!
echo == %DIR% deployed
exit /b 0

rem ---- an archive the distributions are linked from: copied as it is under its generic name
:archive
set "TARGET=%EXT%\%~1"
call :stamped %~1 %~2
if exist "%TARGET%" if "%FORCE%"=="0" if "!STAMPED!"=="1" (
  echo == %TARGET% present
  exit /b 0
)
call :fetch %~2 || exit /b 1
copy /y "!FILE!" "%TARGET%" >nul || exit /b 1
>"%EXT%\.%~1.url" echo !%~2!
echo == %TARGET% deployed
exit /b 0

rem ---- STAMPED=1 when external\.<name>.url holds the URL of the variable named %2, what externals.conf names now
:stamped
set "STAMPED=0"
set "STAMP="
if exist "%EXT%\.%~1.url" set /p STAMP=<"%EXT%\.%~1.url"
if "!STAMP!"=="!%~2!" set "STAMPED=1"
exit /b 0

rem ---- the archive of the URL held by the variable named %1, into the downloads folder unless it is there; sets FILE
:fetch
set "URL=!%~1!"
for %%f in ("!URL!") do set "NAME=%%~nxf"
set "SRC=!URL!"
if defined MIRROR set "SRC=!MIRROR!/!NAME!"
set "FILE=%DL%\!NAME!"
if exist "!FILE!" if "%FORCE%"=="0" exit /b 0
echo == downloading !NAME! from !SRC!
"%CURL%" -fL !AUTH! -o "!FILE!.part" "!SRC!" || exit /b 1
move /y "!FILE!.part" "!FILE!" >nul
exit /b 0
