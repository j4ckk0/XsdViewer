@echo off
rem JToolsFramework's devkit on Windows, at the version jtools.conf names (JTOOLS_VERSION): scripts/linux/jtools's twin. It
rem fetches the devkit and runs what is asked of it (doc/DEVKIT.md): sync [--check], version, new NAME ..., --help.
rem
rem Where the devkit comes from, in this order: JTOOLS_HOME (a clone of JToolsFramework, in jtools.conf or the
rem environment); the local Maven repository (%USERPROFILE%\.m2\repository, M2_REPO says another); the MIRROR, with
rem MIRROR_TOKEN or MIRROR_USER and MIRROR_PASSWORD; Maven Central. A download is checked against the .sha1 beside it,
rem then unpacked into external\jtools-devkit-VERSION (outside a project: %USERPROFILE%\.cache\jtools, JTOOLS_CACHE says
rem another). Needs curl, tar and certutil: Windows 10 and later have them.
setlocal EnableExtensions DisableDelayedExpansion
rem what `new` makes outside a project when JTOOLS_VERSION says nothing: this file's own version (scripts/check-versions.sh)
set "JTOOLS_DEFAULT_VERSION=1.5.0"
set "CENTRAL=https://repo1.maven.org/maven2/org/jtools/framework/jtools-devkit"
rem this script's folder, read before any shift: shift moves %0 too
set "HERE=%~dp0"
rem Windows' own curl and tar: a Git or Cygwin tar earlier on the PATH cannot read a jar
set "CURL=%SystemRoot%\System32\curl.exe"
if not exist "%CURL%" set "CURL=curl"
set "TAR=%SystemRoot%\System32\tar.exe"
if not exist "%TAR%" set "TAR=tar"

rem a command known before anything is fetched: --help, or a typing error, costs no download
set "COMMAND=%~1"
if "%COMMAND%"=="sync" goto :known
if "%COMMAND%"=="version" goto :known
if "%COMMAND%"=="new" goto :known
if "%COMMAND%"=="-h" goto :help
if "%COMMAND%"=="--help" goto :help
if not "%COMMAND%"=="" echo jtools: no command %COMMAND% 1>&2
call :usage 1>&2
exit /b 2
:help
call :usage
exit /b 0

:known
rem the arguments after the command, for sync and new
set "REST="
:rest
shift
if "%~1"=="" goto :project
set REST=%REST% "%~1"
goto :rest

:project
set "PROJECT="
rem a project's own copy, in its scripts\windows\ (not the one installed in %USERPROFILE%\.local\bin)
if /i "%HERE:~-17%"=="\scripts\windows\" if exist "%HERE%..\..\jtools.conf" for %%p in ("%HERE%..\..") do set "PROJECT=%%~fp"
rem KEY=VALUE files, # comments: what is set already (the environment, then the files read before) wins
if defined PROJECT (
  call :conf "%PROJECT%\jtools.conf"
  call :conf "%PROJECT%\externals.local.conf"
  call :conf "%PROJECT%\externals.conf"
)
set "VERSION=%JTOOLS_VERSION%"
if not defined VERSION set "VERSION=%JTOOLS_DEFAULT_VERSION%"

rem ---- the devkit: DEVKIT (the files a project carries), STARTER (new-project.bat and the template), FROM ----
if not defined JTOOLS_HOME goto :cached
set "BASE=%CD%"
if defined PROJECT set "BASE=%PROJECT%"
set "CLONE=%JTOOLS_HOME%"
if not "%CLONE:~1,1%"==":" if not "%CLONE:~0,1%"=="\" set "CLONE=%BASE%\%CLONE%"
for %%h in ("%CLONE%") do set "CLONE=%%~fh"
set "MSG=JTOOLS_HOME=%JTOOLS_HOME%: not a clone of JToolsFramework, no devkit\ nor starter\ there"
if not exist "%CLONE%\devkit\" goto :die
if not exist "%CLONE%\starter\" goto :die
set "DEVKIT=%CLONE%\devkit"
set "STARTER=%CLONE%\starter"
set "FROM=%CLONE% (JTOOLS_HOME, its working tree)"
goto :run

:cached
set "CACHE=%JTOOLS_CACHE%"
if not defined CACHE if defined PROJECT set "CACHE=%PROJECT%\external"
if not defined CACHE set "CACHE=%USERPROFILE%\.cache\jtools"
set "DEVKIT=%CACHE%\jtools-devkit-%VERSION%"
set "STARTER=%DEVKIT%\starter"
set "NAME=jtools-devkit-%VERSION%.jar"
set "REPOSITORY=%M2_REPO%"
if not defined REPOSITORY set "REPOSITORY=%USERPROFILE%\.m2\repository"
set "M2JAR=%REPOSITORY%\org\jtools\framework\jtools-devkit\%VERSION%\%NAME%"
rem the jar of ~\.m2 by its date and size: another one is unpacked again
set "M2STAMP="
if exist "%M2JAR%" for %%j in ("%M2JAR%") do set "M2STAMP=%%~tj %%~zj"
set "UNPACKED="
if exist "%DEVKIT%\.m2-stamp" for /f "usebackq delims=" %%s in ("%DEVKIT%\.m2-stamp") do set "UNPACKED=%%s"
set "FROM=%DEVKIT% (unpacked before)"
if exist "%DEVKIT%\version" if not defined M2STAMP goto :run
if exist "%DEVKIT%\version" if "%UNPACKED%"=="%M2STAMP%" goto :run

set "WORK=%CACHE%\jtools-devkit-%VERSION%.tmp"
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%\unpacked"
if not defined M2STAMP goto :download
copy /y "%M2JAR%" "%WORK%\%NAME%" >nul
set "FROM=%M2JAR%"
goto :unpack

:download
set "SRC=%CENTRAL%/%VERSION%/%NAME%"
set "AUTH="
if not defined MIRROR goto :fetch
if "%MIRROR:~-1%"=="/" set "MIRROR=%MIRROR:~0,-1%"
set "SRC=%MIRROR%/%NAME%"
if not defined MIRROR_TOKEN_HEADER set "MIRROR_TOKEN_HEADER=Authorization: Bearer"
if defined MIRROR_TOKEN set AUTH=-H "%MIRROR_TOKEN_HEADER% %MIRROR_TOKEN%"
if defined MIRROR_USER set AUTH=%AUTH% -u "%MIRROR_USER%:%MIRROR_PASSWORD%"
:fetch
echo == downloading %NAME% from %SRC% 1>&2
set "MSG=download failed: %SRC% (JTOOLS_VERSION=%VERSION%: is there such a version?)"
"%CURL%" -fsSL %AUTH% -o "%WORK%\%NAME%" "%SRC%" || goto :die
set "MSG=no checksum beside it: %SRC%.sha1"
"%CURL%" -fsSL %AUTH% -o "%WORK%\%NAME%.sha1" "%SRC%.sha1" || goto :die
set "EXPECTED="
for /f "usebackq tokens=1" %%e in ("%WORK%\%NAME%.sha1") do if not defined EXPECTED set "EXPECTED=%%e"
set "ACTUAL="
for /f "skip=1 delims=" %%h in ('certutil -hashfile "%WORK%\%NAME%" SHA1') do if not defined ACTUAL set "ACTUAL=%%h"
set "ACTUAL=%ACTUAL: =%"
set "MSG=%SRC%: its SHA-1 is %ACTUAL%, the .sha1 beside it says %EXPECTED% - not unpacked"
if /i not "%EXPECTED%"=="%ACTUAL%" goto :die
set "FROM=%SRC%"

:unpack
set "MSG=%NAME%: no jtools-devkit\ inside"
"%TAR%" -xf "%WORK%\%NAME%" -C "%WORK%\unpacked" || goto :die
if not exist "%WORK%\unpacked\jtools-devkit\" goto :die
if exist "%DEVKIT%" rmdir /s /q "%DEVKIT%"
move "%WORK%\unpacked\jtools-devkit" "%DEVKIT%" >nul || goto :die
> "%DEVKIT%\.m2-stamp" echo(%M2STAMP%
rmdir /s /q "%WORK%"

:run
if "%COMMAND%"=="version" goto :version
if "%COMMAND%"=="sync" goto :sync
call "%STARTER%\new-project.bat" %REST%
exit /b %ERRORLEVEL%

:sync
set "MSG=sync refreshes a project: run the scripts\windows\jtools inside it"
if not defined PROJECT goto :die
call "%DEVKIT%\jtools-sync.bat" "%PROJECT%" %REST%
exit /b %ERRORLEVEL%

:version
set "DEVKIT_VERSION="
if exist "%DEVKIT%\version" for /f "usebackq delims=" %%v in ("%DEVKIT%\version") do if not defined DEVKIT_VERSION set "DEVKIT_VERSION=%%v"
if not defined DEVKIT_VERSION for /f "tokens=3 delims=<>" %%v in ('findstr /c:"<jtools.version>" "%DEVKIT%\..\conventions\parent\pom.xml"') do if not defined DEVKIT_VERSION set "DEVKIT_VERSION=%%v"
echo jtools-devkit %DEVKIT_VERSION%, from %FROM%
if defined PROJECT if not defined JTOOLS_HOME echo ^(jtools.conf names %VERSION%^)
exit /b 0

:conf
if not exist "%~1" exit /b 0
for /f "usebackq eol=# tokens=1* delims==" %%a in ("%~1") do if not defined %%a set "%%a=%%b"
exit /b 0

:usage
echo jtools sync            the managed files (scripts\linux\*, scripts\windows\*, .githooks\pre-push, .gitattributes) refreshed at
echo                        JTOOLS_VERSION; what else differs said, not changed (the pom's framework version,
echo                        the externals' versions)
echo jtools sync --check    the same, changing nothing; exit 1 when anything differs (a project's CI)
echo jtools version         the devkit this project names, and where it was found
echo jtools new NAME ...    a new project, from anywhere - outside a project too (doc/GETTING-STARTED.md):
echo                        [--dir DIR] [--id ID] [--port N] [--bare DIR] [--no-git] [--no-build]
exit /b 0

:die
echo jtools: %MSG% 1>&2
if defined WORK if exist "%WORK%" rmdir /s /q "%WORK%"
exit /b 1
