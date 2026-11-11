@echo off
rem Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
rem
rem The build, every test included: mvn with jtools.conf's BUILD_GOALS ("clean package" unless it says otherwise).
rem
rem   scripts\windows\build.bat
rem   scripts\windows\build.bat -DskipTests        extra arguments are passed to mvn
rem
cd /d "%~dp0..\.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (jtools.conf; setup done? it is offered otherwise; the externals first)
call mvn %BUILD_GOALS% %*
exit /b %errorlevel%
