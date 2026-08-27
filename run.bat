@echo off
rem
rem Build (if needed) and run XsdViewer on Windows.
rem
rem   run.bat                          start the server and open the browser
rem   run.bat samples\purchaseOrder.xsd
rem   run.bat --port 9090 --no-browser some.xsd
rem   run.bat --rebuild                force a rebuild first
rem
rem Any other argument is passed to the tool (see: run.bat --help).
rem
setlocal EnableDelayedExpansion

cd /d "%~dp0"
set "JAR=target\xsdviewer.jar"

set "REBUILD=0"
set "ARGS="
for %%a in (%*) do (
  if /i "%%~a"=="--rebuild" (
    set "REBUILD=1"
  ) else (
    set "ARGS=!ARGS! %%a"
  )
)

where java >nul 2>nul || (
  echo java not found in PATH ^(Java 21 required^) >&2
  exit /b 1
)

rem Rebuild when asked, when the jar is missing, or when any source is newer than it.
set "NEED_BUILD=%REBUILD%"
if not exist "%JAR%" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$jar = (Get-Item '%JAR%').LastWriteTimeUtc;" ^
    "$src = @(Get-Item pom.xml) + @(Get-ChildItem src -Recurse -File);" ^
    "if ($src | Where-Object { $_.LastWriteTimeUtc -gt $jar }) { exit 1 } else { exit 0 }"
  if errorlevel 1 set "NEED_BUILD=1"
)

if "%NEED_BUILD%"=="1" (
  where mvn >nul 2>nul || (
    echo mvn not found in PATH, cannot build %JAR% >&2
    exit /b 1
  )
  echo == building %JAR%
  call mvn -q package || exit /b 1
)

java -jar "%JAR%" %ARGS%
exit /b %errorlevel%
