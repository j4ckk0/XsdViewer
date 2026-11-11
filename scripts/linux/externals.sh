#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
#
# Sourced by the scripts, from the project root. Reads jtools.conf — what the scripts know of this project — and
# makes sure scripts/linux/setup.sh has been run: every tool externals.conf names is deployed in external/ (offered at a
# terminal when something is missing; elsewhere, the CI or a hook, it stops with the way out). Then puts the
# externals in front: external/jdk as JAVA_HOME, its java, Maven's mvn, and Gradle's and Node's when deployed.
#
# scripts/linux/externals.local.sh, when the project has one, is sourced first: it may define missing_local_externals
# (prints what of its own extras is missing) and export what its tools need.
#
# Sets, for the scripts: APP_ID, APP_NAME, APP_MODULE, BUILD_GOALS (jtools.conf), $jar (the program's jar, when
# APP_ID names one), require_program and refuse_while_running.
#
ext="$PWD/external"

# jtools.conf: KEY=VALUE lines, # comments; a value is the rest of its line, spaces included. What the environment
# sets already wins, as it does for scripts/linux/jtools: BUILD_GOALS=verify scripts/linux/build.sh
[ -f jtools.conf ] || { echo "no jtools.conf in $PWD: not a project of JToolsFramework's" >&2; exit 1; }
while IFS='=' read -r key value; do
  key=${key%$'\r'} value=${value%$'\r'}   # a file written on Windows (CRLF) reads the same
  case "$key" in ''|\#*) continue ;; esac
  [ -n "${!key:-}" ] || printf -v "$key" '%s' "$value"
done < jtools.conf
APP_ID=${APP_ID:-} APP_NAME=${APP_NAME:-} APP_MODULE=${APP_MODULE:-app} BUILD_GOALS=${BUILD_GOALS:-clean package}

# The computer the project is developed on — Linux, or Windows through Git Bash — and its tools' launchers.
case "$(uname -s)" in
  Linux) host=LINUX java=bin/java node=bin/node host_archive=jdk-linux.tar.gz ;;
  MINGW*|MSYS*|CYGWIN*) host=WINDOWS java=bin/java.exe node=node.exe host_archive=jdk-windows.zip ;;
  *) echo "$(uname -s): a project on JToolsFramework is developed on Linux or on Windows" >&2; exit 1 ;;
esac

# Whether externals.conf names a tool: what it does not name, this project does without.
wants() { grep -q "^$1=" externals.conf; }

[ -f scripts/linux/externals.local.sh ] && . scripts/linux/externals.local.sh

# The URL an external was deployed from (external/.<name>.url, which setup writes): one externals.conf no longer names
# — a new version — is as good as missing.
url_of() { sed -n "s|^$1=||p" externals.conf | head -1 | tr -d '\r'; }
current() { [ "$(cat "$ext/.$1.url" 2>/dev/null)" = "$(url_of "$2")" ]; }

# What setup deploys and is not there, or is of another version than externals.conf's; empty when all is.
missing_externals() {
  local m=
  wants "JDK_$host" && { [ -x "$ext/jdk/$java" ] && current jdk "JDK_$host" || m="$m jdk/"; }
  wants "MAVEN_$host" && { [ -x "$ext/maven/bin/mvn" ] && current maven "MAVEN_$host" || m="$m maven/"; }
  wants GRADLE && { [ -x "$ext/gradle/bin/gradle" ] && current gradle GRADLE || m="$m gradle/"; }
  wants "NODE_$host" && { [ -x "$ext/node/$node" ] && current node "NODE_$host" || m="$m node/"; }
  wants "JDK_$host" && { [ -s "$ext/$host_archive" ] && current "$host_archive" "JDK_$host" || m="$m $host_archive"; }
  wants JMODS_LINUX && { [ -s "$ext/jmods-linux.tar.gz" ] && current jmods-linux.tar.gz JMODS_LINUX || m="$m jmods-linux.tar.gz"; }
  wants JMODS_WINDOWS && { [ -s "$ext/jmods-windows.zip" ] && current jmods-windows.zip JMODS_WINDOWS || m="$m jmods-windows.zip"; }
  wants JMODS_MACOS && { [ -s "$ext/jmods-macos.tar.gz" ] && current jmods-macos.tar.gz JMODS_MACOS || m="$m jmods-macos.tar.gz"; }
  # a JDK up to 23 carries its jmods: another platform's JDK archive, for a platform with no JMODS_*
  [ "$host" = LINUX ] || { wants JDK_LINUX && ! wants JMODS_LINUX && { [ -s "$ext/jdk-linux.tar.gz" ] && current jdk-linux.tar.gz JDK_LINUX || m="$m jdk-linux.tar.gz"; }; }
  [ "$host" = WINDOWS ] || { wants JDK_WINDOWS && ! wants JMODS_WINDOWS && { [ -s "$ext/jdk-windows.zip" ] && current jdk-windows.zip JDK_WINDOWS || m="$m jdk-windows.zip"; }; }
  wants JDK_MACOS && ! wants JMODS_MACOS && { [ -s "$ext/jdk-macos.tar.gz" ] && current jdk-macos.tar.gz JDK_MACOS || m="$m jdk-macos.tar.gz"; }
  if declare -F missing_local_externals >/dev/null; then m="$m$(missing_local_externals)"; fi
  echo "$m"
}

missing=$(missing_externals)
if [ -n "$missing" ]; then
  echo "external/ is not set up: missing, or of another version than externals.conf's:$missing (scripts/linux/setup.sh deploys them)" >&2
  if [ -t 0 ] && [ -t 2 ]; then
    read -r -p "Run scripts/linux/setup.sh now? [Y/n] " answer
    case "$answer" in ''|[Yy]*) scripts/linux/setup.sh ;; *) exit 1 ;; esac
    missing=$(missing_externals)
    [ -z "$missing" ] || { echo "still missing$missing" >&2; exit 1; }
  else
    exit 1
  fi
fi

export JAVA_HOME="$ext/jdk"
export PATH="$ext/jdk/bin:$ext/maven/bin:$PATH"
[ -d "$ext/gradle/bin" ] && export PATH="$ext/gradle/bin:$PATH"
[ -x "$ext/node/$node" ] && export PATH="$(dirname "$ext/node/$node"):$PATH"

jar=
[ -n "$APP_ID" ] && jar="$APP_MODULE/target/$APP_ID.jar"

# Stops a script that runs or packages the program when the project has none (a library: APP_ID empty).
require_program() {
  [ -n "$APP_ID" ] || { echo "jtools.conf names no program (APP_ID): there is nothing to ${1:-run} in this project" >&2; exit 1; }
}

# Refuses to go on while the program runs from the jar a build is about to replace: Maven would otherwise fail
# deleting it, with an error saying nothing of the cause. $1 is what the caller is about to do.
refuse_while_running() {
  [ -n "$APP_ID" ] || return 0
  command -v pgrep >/dev/null || return 0   # Git Bash has none: Windows' own lock on the jar says it then
  local pids
  # anchored on the process's own java: a shell command line that merely names the jar is not an instance
  pids=$(pgrep -f "^[^[:space:]]*java[^[:space:]]* .*-jar[[:space:]].*$APP_ID\\.jar" || true)
  [ -n "$pids" ] || return 0
  echo "$APP_NAME is running (pid$( [ "$(echo "$pids" | wc -l)" -gt 1 ] && echo s) $(echo "$pids" | tr '\n' ' ')): close its pages or run scripts/linux/stop.sh before ${1:-building}, or it holds the jar the build replaces." >&2
  exit 1
}
