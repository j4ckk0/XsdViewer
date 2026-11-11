@echo off
rem Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
rem
rem Build the self-contained distributions (jar + a runtime jlink trims to what the program needs + launchers;
rem <name>.exe without a console), into releases\:
rem   releases\<id>-<version>-linux.tar.gz, -windows.zip, and -macos.tar.gz when externals.conf names JMODS_MACOS
rem
rem   scripts\windows\package.bat
rem   scripts\windows\package.bat -DskipTests      extra arguments are passed to mvn
rem
rem The runtimes are linked from the jmods of external\ (scripts\windows\setup.bat deploys them). The version is the root pom's <revision>, or its own <version>.
rem The releases of the previous versions are removed once the new one is built.
rem
cd /d "%~dp0..\.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (jtools.conf; setup done? it is offered otherwise; the externals first)
if not defined APP_ID (
  echo jtools.conf names no program ^(APP_ID^): there is nothing to package in this project >&2
  exit /b 1
)

call mvn %BUILD_GOALS% -Pdist %* || exit /b 1

for /f "delims=" %%l in ('findstr /r /c:"<revision>.*</revision>" pom.xml') do if not defined VERSION_LINE set "VERSION_LINE=%%l"
if not defined VERSION_LINE goto :own_version
set "VERSION=%VERSION_LINE:*<revision>=%"
set "VERSION=%VERSION:</revision>=%"
goto :versioned
:own_version
rem a pom with no ${revision}: its own <version>, the first one before or after its <parent>, found by line numbers
set "PARENT_START="
set "PARENT_END="
for /f "delims=:" %%n in ('findstr /n /c:"<parent>" pom.xml') do if not defined PARENT_START set "PARENT_START=%%n"
for /f "delims=:" %%n in ('findstr /n /c:"</parent>" pom.xml') do if not defined PARENT_END set "PARENT_END=%%n"
if not defined PARENT_START set "PARENT_START=0"
if not defined PARENT_END set "PARENT_END=0"
for /f "tokens=1* delims=:" %%a in ('findstr /n /c:"<version>" pom.xml') do (
  if not defined VERSION_LINE if %%a LSS %PARENT_START% set "VERSION_LINE=%%b"
  if not defined VERSION_LINE if %%a GTR %PARENT_END% set "VERSION_LINE=%%b"
)
if not defined VERSION_LINE (
  echo pom.xml names no version - its ^<revision^>, or its own ^<version^>: the scripts read it there >&2
  exit /b 1
)
set "VERSION=%VERSION_LINE:*<version>=%"
set "VERSION=%VERSION:</version>=%"
:versioned
for %%a in (releases\%APP_ID%-*-windows.zip releases\%APP_ID%-*-linux.tar.gz releases\%APP_ID%-*-macos.tar.gz) do call :previous "%%a"

rem What every distribution must carry: the jar, its launchers, the .ini and the runtime.
set "BASE=%APP_ID%-%VERSION%"
call :check "releases\%BASE%-windows.zip" %APP_ID%.jar "%APP_NAME%.exe" %APP_ID%.bat %APP_ID%.ini README.md jre/bin/java.exe jre/release || exit /b 1
call :check "releases\%BASE%-linux.tar.gz" %APP_ID%.jar %APP_ID%.sh %APP_ID%.ini README.md jre/bin/java jre/release || exit /b 1
set "MACOS="
if exist external\jmods-macos.tar.gz set "MACOS=1"
if exist external\jdk-macos.tar.gz set "MACOS=1"
if defined MACOS call :check "releases\%BASE%-macos.tar.gz" %APP_ID%.jar %APP_ID%.sh %APP_ID%.ini README.md jre/bin/java jre/release || exit /b 1
echo == releases\ checked: each with its runtime and launchers
exit /b 0

rem ---- one archive (%1) and the files it must carry (%2...)
:check
if not exist "%~1" ( echo no %~1: the build did not make it >&2 & exit /b 1 )
set "ARCHIVE=%~1"
:checkNext
shift
if "%~1"=="" exit /b 0
"%SystemRoot%\System32\tar.exe" -tf "%ARCHIVE%" | findstr /x /c:"%BASE%/%~1" >nul || ( echo %ARCHIVE%: no %~1 >&2 & exit /b 1 )
goto checkNext

rem ---- an archive of another version: removed
:previous
echo %~nx1 | findstr /c:"-%VERSION%-" >nul && exit /b 0
del /q "%~1"
echo == %~1 removed ^(previous release^)
exit /b 0
