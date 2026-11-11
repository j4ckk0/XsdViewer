@echo off
rem Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
rem
rem The project's CI, run locally: the build with every test, quiet, the pages' dependencies installed exactly as
rem their package-lock.json has them (npm ci); then scripts\windows\ci.local.bat, when the project has checks of its own.
rem
rem   scripts\windows\ci.bat
rem
cd /d "%~dp0..\.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (jtools.conf; setup done? it is offered otherwise; the externals first)
call mvn -q %BUILD_GOALS% -Dnpm.install=ci || exit /b 1
if exist scripts\windows\ci.local.bat call scripts\windows\ci.local.bat || exit /b 1
exit /b 0
