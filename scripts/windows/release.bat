@echo off
rem Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
rem
rem Publishes the version the root pom names - release.sh's twin -, once its tag v<version> exists and
rem scripts\windows\package.bat has made its archives in releases\: the notes are CHANGELOG.md's "## <version>"
rem section, the archives' SHA-256 added beneath.
rem   - with RELEASE_GITHUB=<owner>/<repository> in jtools.conf: a GitHub Release from the tag, pushed there first,
rem     every releases\<id>-<version>* file attached. Its token: %GITHUB_TOKEN%, else the one line of
rem     %USERPROFILE%\.config\github\<id>-release-token - a fine-grained token for that repository, Contents: read and
rem     write. Never shown.
rem   - without it, a local release: releases\<id>-<version>.sha256 and releases\<id>-<version>-notes.md beside them.
rem
rem   scripts\windows\release.bat               publish
rem   scripts\windows\release.bat --draft       a GitHub draft, to read on its page before publishing it there
rem   scripts\windows\release.bat --dry-run     the notes printed, and the request a GitHub Release would send written
rem                                             to releases\<id>-<version>-release.json; nothing published
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0..\.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (jtools.conf; setup done? it is offered otherwise)
if not defined APP_ID (
  echo jtools.conf names no program ^(APP_ID^): there is nothing to release in this project >&2
  exit /b 1
)
set "DRAFT=false"
set "DRY="
:args
if "%~1"=="" goto :parsed
if "%~1"=="--draft" (
  set "DRAFT=true"
  shift
  goto :args
)
if "%~1"=="--dry-run" (
  set "DRY=1"
  shift
  goto :args
)
echo unknown option: %~1 ^(--draft, --dry-run^) >&2
exit /b 2
:parsed
set "CURL=%SystemRoot%\System32\curl.exe"
if not exist "%CURL%" set "CURL=curl"

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
set "TAG=v%VERSION%"
set "BASE=%APP_ID%-%VERSION%"
set "WORK=%TEMP%\jtools-release-%RANDOM%"
mkdir "%WORK%" || exit /b 1
rem CHANGELOG.md's UTF-8 goes through as bytes, in the console's own code page: one character a byte, read and written
rem back alike (in UTF-8, chcp 65001, set /p mangles what is not ASCII)
call :release
set "STATUS=%errorlevel%"
rd /s /q "%WORK%" 2>nul
exit /b %STATUS%

:release
rem What is published: every file of releases\ named after this version, but what this script writes there itself
set "COUNT=0"
type nul > "%WORK%\files.txt"
for %%f in ("releases\%BASE%-*" "releases\%BASE%.*") do call :file "%%~nxf"
if "%COUNT%"=="0" (
  echo no releases\%BASE%*: run scripts\windows\package.bat first >&2
  exit /b 1
)

rem The notes: CHANGELOG.md's section of this version - its "## <version>" line, a date may follow, to the next "## " -,
rem its blank lines at either end left out
if not exist CHANGELOG.md (
  echo no CHANGELOG.md: the notes are its "## %VERSION%" section >&2
  exit /b 1
)
set "START="
set "END="
for /f "tokens=1,2 delims= " %%a in ('findstr /n /b /c:"## " CHANGELOG.md') do for /f "delims=:" %%n in ("%%a") do (
  if defined START if not defined END set "END=%%n"
  if not defined START if "%%b"=="%VERSION%" set "START=%%n"
)
if not defined START (
  echo CHANGELOG.md has no "## %VERSION%" section >&2
  exit /b 1
)
if not defined END set "END=999999"
set "FIRST="
set "LAST="
for /f "delims=:" %%n in ('findstr /n /r /c:"[^ ]" CHANGELOG.md') do (
  if %%n GTR %START% if %%n LSS %END% (
    if not defined FIRST set "FIRST=%%n"
    set "LAST=%%n"
  )
)
if not defined FIRST (
  echo CHANGELOG.md's "## %VERSION%" section is empty >&2
  exit /b 1
)
rem the section's lines as they are, for the notes; the same lines as a JSON string, for the request: \ and " escaped,
rem tabs \t, spaces \u0020 (set /p would drop a line's leading ones), each line after the first opened by \n
set "C=0"
(for /f "delims=" %%l in ('findstr /n "^" CHANGELOG.md') do (
  set /a C+=1
  set "L=%%l"
  setlocal EnableDelayedExpansion
  if !C! GEQ %FIRST% if !C! LEQ %LAST% (
    set "L=!L:*:=!"
    echo(!L!
  )
  endlocal
)) > "%WORK%\section.md"
call :sums || exit /b 1
copy /y "%WORK%\section.md" "%WORK%\notes.md" >nul
>> "%WORK%\notes.md" (
  echo(
  echo ## Checksums ^(SHA-256^)
  echo(
  echo ```
  type "%WORK%\sums.txt"
  echo ```
)

if defined RELEASE_GITHUB goto :github
rem A local release: the tag here, the checksums and the notes beside the archives
where git >nul 2>&1 && if exist .git (
  git rev-parse -q --verify "refs/tags/%TAG%" >nul || (
    echo no tag %TAG%: git tag -a %TAG% -m "%VERSION%" first >&2
    exit /b 1
  )
)
if defined DRY (
  type "%WORK%\notes.md"
  echo ^(dry run: no RELEASE_GITHUB in jtools.conf, a local release; nothing written^)
  exit /b 0
)
copy /y "%WORK%\sums.txt" "releases\%BASE%.sha256" >nul || exit /b 1
copy /y "%WORK%\notes.md" "releases\%BASE%-notes.md" >nul || exit /b 1
echo == released locally: releases\%BASE%.sha256 and releases\%BASE%-notes.md, beside %COUNT% files
exit /b 0

:github
set "REQUEST=releases\%BASE%-release.json"
call :request > "%REQUEST%" || exit /b 1
if defined DRY (
  type "%WORK%\notes.md"
  echo ^(dry run: the request for %RELEASE_GITHUB% is in %REQUEST%; nothing published^)
  exit /b 0
)
set "TOKEN_FILE=%USERPROFILE%\.config\github\%APP_ID%-release-token"
if not defined GITHUB_TOKEN if exist "%TOKEN_FILE%" set /p GITHUB_TOKEN=<"%TOKEN_FILE%"
if not defined GITHUB_TOKEN (
  del /q "%REQUEST%"
  echo no token: set GITHUB_TOKEN, or write it to %TOKEN_FILE% ^(one line^) >&2
  exit /b 1
)
rem the headers through a file of curl's own (-H @file): the token never on a command line
> "%WORK%\headers.txt" (
  echo Authorization: Bearer %GITHUB_TOKEN%
  echo Accept: application/vnd.github+json
)
set "API=https://api.github.com/repos/%RELEASE_GITHUB%"
rem GitHub would make a tag that is not there, from the default branch: the tag, pushed, is asked for first
call :call "%API%/git/ref/tags/%TAG%"
set "REFUSED="
if "%CODE%"=="401" set "REFUSED=1"
if "%CODE%"=="403" set "REFUSED=1"
if defined REFUSED (
  del /q "%REQUEST%"
  echo github.com refuses the token ^(HTTP %CODE%^): expired, or without Contents: read and write on %RELEASE_GITHUB% >&2
  exit /b 1
)
if not "%CODE%"=="200" (
  del /q "%REQUEST%"
  echo tag %TAG% is not on github.com/%RELEASE_GITHUB% ^(HTTP %CODE%^): push it first >&2
  exit /b 1
)
call :call -X POST "%API%/releases" --data-binary "@%REQUEST%"
del /q "%REQUEST%"
if not "%CODE%"=="201" (
  echo release not created ^(HTTP %CODE%^) >&2
  type "%WORK%\answer.json" >&2
  exit /b 1
)
rem the answer is indented JSON, a field a line: the release's own id and page come before any other's
set "ID="
set "LINK="
for /f "tokens=2 delims=:, " %%i in ('findstr /c:"\"id\":" "%WORK%\answer.json"') do if not defined ID set "ID=%%i"
for /f "tokens=1* delims=:" %%a in ('findstr /c:"\"html_url\":" "%WORK%\answer.json"') do if not defined LINK set "LINK=%%b"
set "LINK=%LINK:"=%"
set "LINK=%LINK:,=%"
set "LINK=%LINK: =%"
if "%DRAFT%"=="true" (echo == release %TAG% created on github.com/%RELEASE_GITHUB%, a draft) else echo == release %TAG% created on github.com/%RELEASE_GITHUB%
for /f "usebackq delims=" %%f in ("%WORK%\files.txt") do (
  call :call -X POST -H "Content-Type: application/octet-stream" --data-binary "@releases\%%f" "https://uploads.github.com/repos/%RELEASE_GITHUB%/releases/%ID%/assets?name=%%f"
  call :uploaded "%%f" || exit /b 1
)
echo == %LINK%
exit /b 0

rem ---- one file of releases\ (%1, its name): published, unless it is this script's own or another version's
:file
set "NAME=%~1"
if /i "%NAME:~-7%"==".sha256" exit /b 0
if /i "%NAME:~-9%"=="-notes.md" exit /b 0
if /i "%NAME:~-13%"=="-release.json" exit /b 0
echo %NAME%| findstr /r /b /c:"%BASE%\.[0-9]" >nul && exit /b 0
>> "%WORK%\files.txt" echo %NAME%
set /a COUNT+=1
exit /b 0

rem ---- the published files' SHA-256, as sha256sum writes them: <hash>, two spaces, <name>
:sums
type nul > "%WORK%\sums.txt"
for /f "usebackq delims=" %%f in ("%WORK%\files.txt") do call :sum "%%f" || exit /b 1
exit /b 0
:sum
set "HASH="
for /f "skip=1 delims=" %%h in ('certutil -hashfile "releases\%~1" SHA256') do if not defined HASH set "HASH=%%h"
if not defined HASH (
  echo releases\%~1: no SHA-256 from certutil >&2
  exit /b 1
)
set "HASH=%HASH: =%"
>> "%WORK%\sums.txt" echo %HASH%  %~1
exit /b 0

rem ---- the request's JSON, on standard output
:request
<nul set /p "={"tag_name":"%TAG%","name":"%APP_NAME% %VERSION%","draft":%DRAFT%,"body":""
set "C=0"
for /f "delims=" %%l in ('findstr /n "^" "%WORK%\notes.md"') do (
  set /a C+=1
  set "L=%%l"
  setlocal EnableDelayedExpansion
  set "L=!L:*:=!"
  if defined L (
    set "L=!L:\=\\!"
    set "L=!L:"=\"!"
    set "L=!L:	=\t!"
    set "L=!L: =\u0020!"
  )
  if !C!==1 (<nul set /p "=!L!") else <nul set /p "=\n!L!"
  endlocal
)
echo \n"}
exit /b 0

rem ---- one call to GitHub (curl's arguments): its HTTP status in CODE, its answer in %WORK%\answer.json
:call
set "CODE="
for /f %%c in ('^""%CURL%" -sS -H "@%WORK%\headers.txt" -o "%WORK%\answer.json" -w "%%{http_code}" %*^"') do set "CODE=%%c"
exit /b 0

rem ---- an upload's answer
:uploaded
if "%CODE%"=="201" (
  echo    uploaded: %~1
  exit /b 0
)
echo %~1 not uploaded ^(HTTP %CODE%^) >&2
type "%WORK%\answer.json" >&2
exit /b 1
