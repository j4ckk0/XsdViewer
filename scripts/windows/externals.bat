@echo off
rem Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
rem
rem Called by the scripts, from the project root. Reads jtools.conf — what the scripts know of this project — and
rem makes sure scripts\windows\setup.bat has been run: every tool externals.conf names is deployed in external\ (offered with
rem a 30 s timeout, No by default: the CI does not hang). Then puts the externals in front: external\jdk as JAVA_HOME,
rem its java.exe, Maven's mvn.cmd, and Gradle's and Node's when deployed. No setlocal: the caller keeps what is set here.
rem
rem Sets APP_ID, APP_NAME, APP_MODULE, BUILD_GOALS (jtools.conf) and JAR, the program's jar when APP_ID names one.
rem
if not exist jtools.conf (
  echo no jtools.conf in %CD%: not a project of JToolsFramework's >&2
  exit /b 1
)
rem what the environment sets already wins, as it does for scripts\windows\jtools.bat: the scripts that call this
rem one have a setlocal of their own, so that nothing set here outlives them in the prompt
for /f "usebackq eol=# tokens=1* delims==" %%a in ("jtools.conf") do if not defined %%a set "%%a=%%b"
if not defined APP_MODULE set "APP_MODULE=app"
if not defined BUILD_GOALS set "BUILD_GOALS=clean package"

set "EXT=%CD%\external"
call :missing
if defined MISSING (
  echo external\ is not set up: missing, or of another version than externals.conf's:%MISSING% ^(scripts\windows\setup.bat deploys them^) >&2
  choice /c YN /n /t 30 /d N /m "Run scripts\windows\setup.bat now? [Y/N] "
  if errorlevel 2 exit /b 1
  call "%~dp0setup.bat" || exit /b 1
  call :missing
  if defined MISSING (
    echo still missing%MISSING% >&2
    exit /b 1
  )
)
set "JAVA_HOME=%EXT%\jdk"
set "PATH=%EXT%\jdk\bin;%EXT%\maven\bin;%PATH%"
if exist "%EXT%\gradle\bin" set "PATH=%EXT%\gradle\bin;%PATH%"
if exist "%EXT%\node" set "PATH=%EXT%\node;%PATH%"
set "JAR="
if defined APP_ID set "JAR=%APP_MODULE%\target\%APP_ID%.jar"
exit /b 0

rem ---- what setup deploys and is not there, or came from another URL than externals.conf names, in MISSING (undefined
rem      when all is there); a tool externals.conf does not name is not wanted
:missing
set "MISSING="
call :wanted JDK_WINDOWS && call :check jdk JDK_WINDOWS "jdk\bin\java.exe" "jdk\"
call :wanted MAVEN_WINDOWS && call :check maven MAVEN_WINDOWS "maven\bin\mvn.cmd" "maven\"
call :wanted GRADLE && call :check gradle GRADLE "gradle\bin\gradle.bat" "gradle\"
call :wanted NODE_WINDOWS && call :check node NODE_WINDOWS "node\node.exe" "node\"
call :wanted JDK_WINDOWS && call :check jdk-windows.zip JDK_WINDOWS jdk-windows.zip jdk-windows.zip
call :wanted JMODS_LINUX && call :check jmods-linux.tar.gz JMODS_LINUX jmods-linux.tar.gz jmods-linux.tar.gz
call :wanted JMODS_WINDOWS && call :check jmods-windows.zip JMODS_WINDOWS jmods-windows.zip jmods-windows.zip
call :wanted JMODS_MACOS && call :check jmods-macos.tar.gz JMODS_MACOS jmods-macos.tar.gz jmods-macos.tar.gz
exit /b 0

rem ---- %3 present under external\ and deployed from the URL externals.conf names for %2 (external\.%1.url), else %4 missing
:check
set "STAMP="
set "WANT="
if exist "%EXT%\.%~1.url" set /p STAMP=<"%EXT%\.%~1.url"
for /f "tokens=1* delims==" %%a in ('findstr /b /c:"%~2=" externals.conf') do set "WANT=%%b"
if not exist "%EXT%\%~3" (set "MISSING=%MISSING% %~4" & exit /b 0)
if not "%STAMP%"=="%WANT%" set "MISSING=%MISSING% %~4"
exit /b 0

:wanted
findstr /b /c:"%~1=" externals.conf >nul
exit /b %errorlevel%
