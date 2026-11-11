#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
#
# Build (if needed) and run the program: it serves the page and its API, opens the browser on it, and stops once
# its last page is closed.
#
#   scripts/linux/run.sh
#   scripts/linux/run.sh --port 9090 --no-browser   # see: scripts/linux/run.sh --help
#   scripts/linux/run.sh --keep-alive               # keep serving once the last page is closed (scripts/linux/stop.sh then)
#   scripts/linux/run.sh --rebuild                  # force a rebuild first
#
# Any other argument is passed to the program.
#
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")/../.."   # the project root
. scripts/linux/externals.sh   # jtools.conf; setup done? (offers it otherwise); the externals first
require_program "run"

rebuild=false
args=()
for a in "$@"; do
  case "$a" in
    --rebuild) rebuild=true ;;
    *) args+=("$a") ;;
  esac
done

# Rebuild when asked, when the jar is missing, or when a file of the project is newer than it — what the builds and
# the tools write left out.
newer_source() {
  find . \( -name .git -o -name target -o -name build -o -name node_modules -o -name .angular -o -name external \
            -o -name releases -o -name tmp \) -prune -o -type f -newer "$jar" -print -quit
}
if $rebuild || [ ! -f "$jar" ] || [ -n "$(newer_source)" ]; then
  echo "== building $jar"
  # clean: Maven leaves in target/ what the sources no longer have (a removed resource would still be served, and shipped)
  # shellcheck disable=SC2086 # BUILD_GOALS is a list of goals
  mvn -q $BUILD_GOALS -DskipTests
fi

# Run a copy of the jar, never the jar itself: a build rewrites the jar in place, and a server still running from it
# would then read garbage out of its own jar (ZipException: invalid LOC header). The copy is written under a name of
# its own and renamed into place, a new file: a copy an earlier run is still reading is left whole, and a build,
# which only writes the jar, never touches it.
run_jar="$(dirname "$jar")/$APP_ID-run.jar"
cp "$jar" "$run_jar.$$"
mv -f "$run_jar.$$" "$run_jar"

exec java -jar "$run_jar" ${args[@]+"${args[@]}"}
