#!/usr/bin/env bash
#
# Build the self-contained distributions (jar + a trimmed runtime made with jlink + launcher):
#   releases/xsdviewer-<version>-windows.zip
#   releases/xsdviewer-<version>-linux.tar.gz
#   releases/xsdviewer-<version>-macos.tar.gz
#   releases/xsdviewer-<version>.jar             (copy of app/target/xsdviewer.jar)
#
#   scripts/package.sh
#   scripts/package.sh -DskipTests      # extra arguments are passed to mvn
#
# Needs Temurin's jmods archives in jre/ — the platforms present are built; the jlink is that of this machine's JDK
# archive in jre/ when there is one, else that of the JDK running Maven, the jmods' version (see README: Packaging).
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."
command -v mvn >/dev/null || { echo "mvn not found in PATH" >&2; exit 1; }

jre=jre
if ! compgen -G "$jre/*jmods*" >/dev/null; then
  echo "no Temurin jmods archive (*jmods*windows*.zip, *jmods*linux*.tar.gz, *jmods*mac*.tar.gz) in $jre/ - download them first (see README: Packaging)" >&2
  exit 1
fi

exec mvn package -Pdist "$@"
