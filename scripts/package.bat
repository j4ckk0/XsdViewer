@echo off
rem
rem Build the self-contained distributions (jar + a trimmed runtime made with jlink + launcher):
rem   releases\xsdviewer-<version>-windows.zip
rem   releases\xsdviewer-<version>-linux.tar.gz
rem   releases\xsdviewer-<version>.jar             (copy of app\target\xsdviewer.jar)
rem
rem   scripts\package.bat
rem   scripts\package.bat -DskipTests      extra arguments are passed to mvn
rem
rem Needs Temurin's jmods archives in jre\, and this machine's JDK archive, whose jlink links the runtimes (see
rem README: Packaging).
rem
cd /d "%~dp0.."
where mvn >nul 2>nul || (
  echo mvn not found in PATH >&2
  exit /b 1
)

set "JRE=jre"
if not exist "%JRE%\*jmods*" (
  echo no Temurin jmods archive in %JRE%\ - download them first ^(see README: Packaging^) >&2
  exit /b 1
)
if not exist "%JRE%\*jdk_*windows*.zip" (
  echo no Temurin JDK archive for this machine ^(*jdk_*windows*.zip^) in %JRE%\: its jlink links the runtimes ^(see README: Packaging^) >&2
  exit /b 1
)

call mvn package -Pdist %*
exit /b %errorlevel%
