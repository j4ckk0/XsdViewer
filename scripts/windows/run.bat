@echo off
rem Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
rem
rem Build (if needed) and run the program on Windows: it serves the page and its API, opens the browser on it, and
rem stops once its last page is closed.
rem
rem   scripts\windows\run.bat
rem   scripts\windows\run.bat --port 9090 --no-browser   see: scripts\windows\run.bat --help
rem   scripts\windows\run.bat --keep-alive               keep serving once the last page is closed (scripts\windows\stop.bat then)
rem   scripts\windows\run.bat --rebuild                  force a rebuild first
rem
rem Any other argument is passed to the program.
rem
setlocal EnableDelayedExpansion

cd /d "%~dp0..\.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (jtools.conf; setup done? it is offered otherwise; the externals first; JAR)
if not defined APP_ID (
  echo jtools.conf names no program ^(APP_ID^): there is nothing to run in this project >&2
  exit /b 1
)

set "REBUILD=0"
set "ARGS="
for %%a in (%*) do (
  if /i "%%~a"=="--rebuild" (
    set "REBUILD=1"
  ) else (
    set "ARGS=!ARGS! %%a"
  )
)

rem Rebuild when asked, when the jar is missing, or when a file of the project changed since run.bat last built it -
rem what the builds and the tools write left out. Windows marks every file it writes "to archive" (attribute A):
rem robocopy lists the files so marked, and the marks are cleared once the build is done. A build of build.bat's
rem leaves them: run.bat builds once more after it.
set "SKIP=target build node_modules .angular external releases tmp .git"
set "NEED_BUILD=%REBUILD%"
if not exist "%JAR%" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" for /f "tokens=*" %%f in ('robocopy . "%TEMP%\jtools-none" /L /S /IA:A /XD %SKIP% /FP /NJH /NJS /NDL /NC /NS /NP 2^>nul') do set "NEED_BUILD=1"

if "%NEED_BUILD%"=="1" (
  echo == building %JAR%
  call mvn -q %BUILD_GOALS% -DskipTests || exit /b 1
  for /f "tokens=*" %%f in ('robocopy . "%TEMP%\jtools-none" /L /S /IA:A /XD %SKIP% /FP /NJH /NJS /NDL /NC /NS /NP 2^>nul') do attrib -A "%%f" >nul
)

java -jar "%JAR%" %ARGS%
exit /b %errorlevel%
