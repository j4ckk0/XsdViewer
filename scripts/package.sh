#!/usr/bin/env bash
#
# Build the self-contained distributions (jar + bundled JRE + launcher):
#   target/xsdviewer-<version>-windows.zip
#   target/xsdviewer-<version>-linux.tar.gz
#
#   scripts/package.sh
#   scripts/package.sh -DskipTests      # extra arguments are passed to mvn
#
# Needs the JRE archives in jre/ (see README: Packaging).
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."
command -v mvn >/dev/null || { echo "mvn not found in PATH" >&2; exit 1; }

jre=jre
for pattern in '*windows*.zip' '*linux*.tar.gz'; do
  if ! compgen -G "$jre/$pattern" >/dev/null; then
    echo "no $pattern in $jre/ - download the JRE archives first (see README: Packaging)" >&2
    exit 1
  fi
done

exec mvn package -Pdist "$@"
