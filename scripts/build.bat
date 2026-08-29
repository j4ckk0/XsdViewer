@echo off
rem
rem Build target\xsdviewer.jar (compile + tests).
rem
rem   scripts\build.bat
rem   scripts\build.bat -DskipTests        extra arguments are passed to mvn
rem
cd /d "%~dp0.."
where mvn >nul 2>nul || (
  echo mvn not found in PATH >&2
  exit /b 1
)
call mvn package %*
exit /b %errorlevel%
