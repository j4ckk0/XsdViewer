@echo off
rem Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
rem
rem Stops the program: its servers running on this machine from <id>.jar (scripts\windows\run.bat, a distribution). Closing
rem its last page stops one too; this is for the others (--keep-alive, --no-browser). Needs wmic, which Windows 11
rem 24H2 no longer installs by default (Settings > Optional features adds it back).
rem
rem   scripts\windows\stop.bat
rem
setlocal
cd /d "%~dp0..\.."
set "APP_ID="
set "APP_NAME="
for /f "usebackq eol=# tokens=1* delims==" %%a in ("jtools.conf") do set "%%a=%%b"
if not defined APP_ID (
  echo jtools.conf names no program ^(APP_ID^): there is nothing to stop in this project >&2
  exit /b 1
)
set "FOUND="
for /f "tokens=2 delims=," %%p in ('wmic process where "name='java.exe' and commandline like '%%%APP_ID%%%.jar%%'" get processid /format:csv 2^>nul ^| findstr /r "[0-9]"') do (
  set "FOUND=1"
  echo == stopping %APP_NAME% ^(pid %%p^)
  taskkill /pid %%p /f >nul
)
if not defined FOUND echo No %APP_NAME% server is running.
exit /b 0
