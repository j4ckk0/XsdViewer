@echo off
rem
rem Build the self-contained distributions (jar + bundled JRE + launcher):
rem   releases\xsdviewer-<version>-windows.zip
rem   releases\xsdviewer-<version>-linux.tar.gz
rem   releases\xsdviewer-<version>.jar             (copy of target\xsdviewer.jar)
rem
rem   scripts\package.bat
rem   scripts\package.bat -DskipTests      extra arguments are passed to mvn
rem
rem Needs the JRE archives in jre\ (see README: Packaging).
rem
cd /d "%~dp0.."
where mvn >nul 2>nul || (
  echo mvn not found in PATH >&2
  exit /b 1
)

set "JRE=jre"
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
