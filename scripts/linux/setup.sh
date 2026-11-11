#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
#
# Deploys the project's externals into external/ (not under source control), from externals.conf — each tool only
# when externals.conf names it:
#   external/jdk, maven, gradle, node    JDK_LINUX, MAVEN_LINUX, GRADLE, NODE_LINUX: extracted, their top directory stripped
#   external/jdk-linux.tar.gz            JDK_LINUX again, as its archive: the jlink that links the distributions' runtimes
#   external/jmods-linux.tar.gz, jmods-windows.zip, jmods-macos.tar.gz   JMODS_*: the jmods each platform's runtime is
#                                        linked from, kept as archives (hard links to the downloads)
#   external/downloads/                  the archives as downloaded (kept: a second run downloads nothing)
# Each external remembers the URL it was deployed from (external/.<name>.url): one externals.conf names at another
# version is deployed again — a new JDK is a line changed there, then this script.
# then the project's own extras: scripts/linux/setup.local.sh, when it has one, is sourced with fetch and deploy at hand,
# $force set, and the options this script does not know in $local_args.
#
#   scripts/linux/setup.sh            deploys what is missing, or deployed from another URL than externals.conf's
#   scripts/linux/setup.sh --force    downloads and deploys everything again
#
# MIRROR=<url> in externals.conf, or in the environment, fetches <MIRROR>/<file name> instead of the URLs;
# MIRROR_TOKEN (+ MIRROR_TOKEN_HEADER) or MIRROR_USER / MIRROR_PASSWORD authenticate there — from
# externals.local.conf (not under source control) or the environment rather than externals.conf.
# JTOOLS_DOWNLOADS=<dir> in the environment keeps the downloads there instead, shared by every project.
# Needs curl, tar and unzip.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/../.."   # the project root

conf=externals.conf
local_conf=externals.local.conf
ext=external
dl=${JTOOLS_DOWNLOADS:-$ext/downloads}
force=false
local_args=()
for arg in "$@"; do
  case "$arg" in
    --force) force=true ;;
    *) local_args+=("$arg") ;;
  esac
done
if [ ${#local_args[@]} -gt 0 ] && [ ! -f scripts/linux/setup.local.sh ]; then
  echo "unknown option: ${local_args[0]} (try --force)" >&2; exit 1
fi

for tool in curl tar unzip; do
  command -v "$tool" >/dev/null || { echo "$tool not found: install it, then run setup again" >&2; exit 1; }
done

# externals.local.conf then externals.conf: KEY=VALUE lines; what is set already (the environment, then the
# local file) wins
read_conf() {
  while IFS='=' read -r key value; do
    key=${key%$'\r'} value=${value%$'\r'}   # a file written on Windows (CRLF) reads the same
    case "$key" in ''|\#*) continue ;; esac
    if [ -z "${!key:-}" ]; then printf -v "$key" '%s' "$value"; fi
  done < "$1"
}
[ -f "$local_conf" ] && read_conf "$local_conf"
read_conf "$conf"

# The computer the project is developed on — Linux, or Windows through Git Bash (scripts\windows\setup.bat does the same
# from cmd): its JDK, Maven and Node.js, and the JDK archive whose jlink links the distributions' runtimes.
case "$(uname -s)" in
  Linux) host=LINUX java=bin/java node=bin/node host_archive=jdk-linux.tar.gz ;;
  MINGW*|MSYS*|CYGWIN*) host=WINDOWS java=bin/java.exe node=node.exe host_archive=jdk-windows.zip ;;
  *) echo "$(uname -s): a project on JToolsFramework is developed on Linux or on Windows" >&2; exit 1 ;;
esac

# The authentication of the mirror, as curl arguments (none for the public URLs).
auth=()
if [ -n "${MIRROR:-}" ]; then
  if [ -n "${MIRROR_TOKEN:-}" ]; then auth=(-H "${MIRROR_TOKEN_HEADER:-Authorization: Bearer} $MIRROR_TOKEN"); fi
  if [ -n "${MIRROR_USER:-}" ]; then auth+=(-u "$MIRROR_USER:${MIRROR_PASSWORD:-}"); fi
fi

# Downloads the archive of a URL into the downloads folder unless it is there; prints its path.
fetch() {
  local url=$1 name=${1##*/}
  local src=$url
  if [ -n "${MIRROR:-}" ]; then src="${MIRROR%/}/$name"; fi
  if [ ! -s "$dl/$name" ] || $force; then
    echo "== downloading $name from ${src%/*}/" >&2
    # explicit failures: set -e is off inside a function called under || (the callers' "|| exit 1")
    curl -fL --progress-bar ${auth[@]+"${auth[@]}"} -o "$dl/$name.part" "$src" || { echo "download failed: $src" >&2; rm -f "${dl:?}/${name:?}.part"; return 1; }
    mv "$dl/$name.part" "$dl/$name" || return 1
  fi
  [ -s "$dl/$name" ] || { echo "no $dl/$name" >&2; return 1; }
  echo "$dl/$name"
}

# Extracts an archive into a directory, the archive's single top-level directory stripped.
deploy() {
  local file=$1 dir=$2
  rm -rf "${dir:?}"
  mkdir -p "$dir"
  case "$file" in
    *.tar.gz|*.tgz) tar -xzf "$file" -C "$dir" --strip-components 1 ;;
    *.zip)
      local tmp
      tmp=$(mktemp -d "$ext/unzip.XXXXXX")
      unzip -q "$file" -d "$tmp"
      local top
      top=$(find "$tmp" -mindepth 1 -maxdepth 1)
      if [ "$(printf '%s\n' "$top" | wc -l)" = 1 ] && [ -d "$top" ]; then (shopt -s dotglob; mv "$top"/* "$dir"/); else (shopt -s dotglob; mv "$tmp"/* "$dir"/); fi
      rm -rf "${tmp:?}" ;;
    *) echo "$file: not a .tar.gz or .zip" >&2; return 1 ;;
  esac
}

mkdir -p "$ext" "$dl"

# Whether an external is deployed from the URL externals.conf names now (external/.<name>.url says the one it came from).
deployed_from() { [ "$(cat "$ext/.$1.url" 2>/dev/null)" = "$2" ]; }
stamp() { printf '%s\n' "$2" > "$ext/.$1.url"; }

# the tools, under their generic names: present when their launcher is (a failed deployment leaves none), from that URL
for spec in "jdk JDK_$host $java" "maven MAVEN_$host bin/mvn" "gradle GRADLE bin/gradle" "node NODE_$host $node"; do
  set -- $spec
  [ -n "${!2:-}" ] || continue   # a tool this project does without
  dir=$ext/$1
  if [ -x "$dir/$3" ] && deployed_from "$1" "${!2}" && ! $force; then echo "== $dir present"; continue; fi
  file=$(fetch "${!2}") || exit 1   # downloaded before the directory is touched
  deploy "$file" "$dir"
  stamp "$1" "${!2}"
  echo "== $dir deployed"
done

# the archives the distributions are made from, under generic names: the host JDK's (its jlink) and each platform's
# jmods, or that platform's JDK archive when it has no JMODS_* (a JDK up to 23 carries its jmods); hard links to the
# downloads, copies across file systems
archives=("$host_archive JDK_$host" "jmods-linux.tar.gz JMODS_LINUX" "jmods-windows.zip JMODS_WINDOWS" "jmods-macos.tar.gz JMODS_MACOS")
[ "$host" = LINUX ] || archives+=("jdk-linux.tar.gz JDK_LINUX JMODS_LINUX")
[ "$host" = WINDOWS ] || archives+=("jdk-windows.zip JDK_WINDOWS JMODS_WINDOWS")
archives+=("jdk-macos.tar.gz JDK_MACOS JMODS_MACOS")
for spec in "${archives[@]}"; do
  set -- $spec
  [ -n "${!2:-}" ] || continue
  [ -n "${3:-}" ] && [ -n "${!3:-}" ] && continue   # its jmods are named apart: its JDK archive is not needed
  target=$ext/$1
  if [ -s "$target" ] && deployed_from "$1" "${!2}" && ! $force; then echo "== $target present"; continue; fi
  file=$(fetch "${!2}") || exit 1
  rm -f "$target"
  ln "$file" "$target" 2>/dev/null || cp "$file" "$target"
  stamp "$1" "${!2}"
  echo "== $target deployed"
done
# the project's own extras
if [ -f scripts/linux/setup.local.sh ]; then . scripts/linux/setup.local.sh; fi

echo "== external/ ready:"
"$ext/jdk/$java" -version 2>&1 | head -1
JAVA_HOME="$PWD/$ext/jdk" "$ext/maven/bin/mvn" -v 2>/dev/null | head -1
if [ -x "$ext/gradle/bin/gradle" ]; then echo "Gradle: $ext/gradle"; fi
node_dir=$(dirname "$ext/node/$node")
if [ -x "$ext/node/$node" ]; then echo "Node.js $("$ext/node/$node" --version), npm $(PATH="$PWD/$node_dir:$PATH" "$node_dir/npm" --version)"; fi
