#!/usr/bin/env bash
#
# Build target/xsdviewer.jar (compile + tests).
#
#   ./build.sh
#   ./build.sh -DskipTests        # extra arguments are passed to mvn
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
command -v mvn >/dev/null || { echo "mvn not found in PATH" >&2; exit 1; }
exec mvn package "$@"
