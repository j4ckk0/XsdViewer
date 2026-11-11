#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
#
# Build the self-contained distributions (jar + a runtime jlink trims to what the program needs + launchers;
# <name>.exe without a console), into releases/:
#   releases/<id>-<version>-linux.tar.gz, -windows.zip, and -macos.tar.gz when externals.conf names JMODS_MACOS
#
#   scripts/linux/package.sh
#   scripts/linux/package.sh -DskipTests      # extra arguments are passed to mvn
#
# The runtimes are linked from the jmods of external/ (scripts/linux/setup.sh deploys them). The version is the root pom's <revision>, or its own <version>.
# The releases of the previous versions are removed once the new one is built: releases/ holds the release of the
# version pom.xml names, and whatever else you put there is left alone.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/../.."   # the project root
. scripts/linux/externals.sh   # jtools.conf; setup done? (offers it otherwise); the externals first
require_program "package"
refuse_while_running "packaging"

# shellcheck disable=SC2086 # BUILD_GOALS is a list of goals
mvn $BUILD_GOALS -Pdist "$@"

version=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' pom.xml | head -1)   # said once, in the root pom
# or, in a pom with no ${revision}, its own <version>: the first one outside its <parent>
[ -n "$version" ] || version=$(sed '/<parent>/,/<\/parent>/d' pom.xml | sed -n 's:.*<version>\(.*\)</version>.*:\1:p' | head -1)
[ -n "$version" ] || { echo "pom.xml names no version (its <revision>, or its own <version>): the scripts read it there" >&2; exit 1; }

# The releases of the previous versions go, now that the new one is there (only ours, named after another version).
for archive in "releases/$APP_ID"-*-windows.zip "releases/$APP_ID"-*-linux.tar.gz "releases/$APP_ID"-*-macos.tar.gz; do
  [ -f "$archive" ] || continue
  case "$archive" in *"-$version-"*) continue ;; esac
  rm -f "$archive"
  echo "== $archive removed (previous release)"
done

# What every distribution must carry: the jar — with the page inside it, when the project has one —, its launchers,
# the .ini and the runtime. Every check gives grep the entries whole, never through a pipe it may leave early: under
# pipefail the writer it leaves behind would fail the pipeline, and the check with it.
has_page=false
compgen -G "*/angular.json" >/dev/null && has_page=true
check_jar=$(mktemp)
trap 'rm -f "$check_jar"' EXIT
base="$APP_ID-$version"
archives=("releases/$base-windows.zip" "releases/$base-linux.tar.gz")
{ [ -s external/jmods-macos.tar.gz ] || [ -s external/jdk-macos.tar.gz ]; } && archives+=("releases/$base-macos.tar.gz")
for archive in "${archives[@]}"; do
  [ -f "$archive" ] || { echo "no $archive: the build did not make it" >&2; exit 1; }
  case "$archive" in
    *.zip) entries=$(unzip -Z1 "$archive"); unzip -p "$archive" "$base/$APP_ID.jar" > "$check_jar"
           needed=("$APP_ID.jar" "$APP_NAME.exe" "$APP_ID.bat" "$APP_ID.ini" README.md jre/bin/java.exe jre/release) ;;
    *)     entries=$(tar tzf "$archive"); tar xzOf "$archive" "$base/$APP_ID.jar" > "$check_jar"
           needed=("$APP_ID.jar" "$APP_ID.sh" "$APP_ID.ini" README.md jre/bin/java jre/release) ;;
  esac
  for file in "${needed[@]}"; do
    grep -qx "$base/$file" <<< "$entries" || { echo "$archive: no $file" >&2; exit 1; }
  done
  if $has_page; then
    jar_entries=$(unzip -Z1 "$check_jar")
    grep -qx 'static/index.html' <<< "$jar_entries" || { echo "$archive: the jar carries no page (static/index.html)" >&2; exit 1; }
  fi
done
echo "== releases/ checked: ${archives[*]#releases/}, each with its runtime and launchers$($has_page && echo ', the page in the jar')"
