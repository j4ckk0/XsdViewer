@echo off
rem Run XsdViewer with the bundled JRE. Arguments are passed to the tool:
rem   xsdviewer.bat [--port N] [--host H] [--no-browser] [file.xsd | name.xsdviewer.json]
rem
rem The server runs in the background (javaw.exe): no console window stays open, the page opens
rem in the browser, File > Quit stops it. A start-up failure is shown in a dialog.
rem   xsdviewer.bat --console ...   runs it in this window instead, with its messages (for diagnostics)
rem For a start with no console window at all (a .bat always flashes one), use XsdViewer.exe.
setlocal EnableDelayedExpansion
set "DIR=%~dp0"
set "CONSOLE=0"
set "ARGS="
for %%a in (%*) do (
  if /i "%%~a"=="--console" (
    set "CONSOLE=1"
  ) else (
    set "ARGS=!ARGS! "%%~a""
  )
)
if "%CONSOLE%"=="1" (
  "%DIR%jre\bin\java.exe" -jar "%DIR%xsdviewer.jar" %ARGS%
  exit /b %errorlevel%
)
start "XsdViewer" "%DIR%jre\bin\javaw.exe" -jar "%DIR%xsdviewer.jar" %ARGS%
