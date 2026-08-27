@echo off
rem
rem Build the self-contained distributions (jar + bundled JRE + launcher):
rem   target\xsdviewer-<version>-windows.zip
rem   target\xsdviewer-<version>-linux.tar.gz
rem
rem   package.bat
rem   package.bat -DskipTests      extra arguments are passed to mvn
rem
rem Needs the JRE archives in src\main\resources\embedded\jre\ (see README: Packaging).
rem
cd /d "%~dp0"
where mvn >nul 2>nul || (
  echo mvn not found in PATH >&2
  exit /b 1
)

set "JRE=src\main\resources\embedded\jre"
if not exist "%JRE%\*windows*.zip" (
  echo no *windows*.zip in %JRE%\ - download the JRE archives first ^(see README: Packaging^) >&2
  exit /b 1
)
if not exist "%JRE%\*linux*.tar.gz" (
  echo no *linux*.tar.gz in %JRE%\ - download the JRE archives first ^(see README: Packaging^) >&2
  exit /b 1
)

call mvn package -Pdist %*
exit /b %errorlevel%
