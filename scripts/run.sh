#!/usr/bin/env bash
#
# Build (if needed) and run XsdViewer.
#
#   scripts/run.sh                          # start the server and open the browser
#   scripts/run.sh samples/purchaseOrder.xsd
#   scripts/run.sh --port 9090 --no-browser some.xsd
#   scripts/run.sh --rebuild                # force a rebuild first
#
# Any other argument is passed to the tool (see: scripts/run.sh --help).
#
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")/.."
JAR=app/target/xsdviewer.jar

rebuild=false
args=()
for a in "$@"; do
  case "$a" in
    --rebuild) rebuild=true ;;
    *) args+=("$a") ;;
  esac
done

command -v java >/dev/null || { echo "java not found in PATH (Java 21 required)" >&2; exit 1; }

# Rebuild when asked, when the jar is missing, or when any source is newer than it.
if $rebuild || [ ! -f "$JAR" ] || [ -n "$(find pom.xml core app -name target -prune -o -newer "$JAR" -print -quit)" ]; then
  command -v mvn >/dev/null || { echo "mvn not found in PATH, cannot build $JAR" >&2; exit 1; }
  echo "== building $JAR"
  mvn -q package
fi

exec java -jar "$JAR" "${args[@]}"
