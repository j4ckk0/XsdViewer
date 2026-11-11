#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
#
# The build, every test included: mvn with jtools.conf's BUILD_GOALS ("clean package" unless it says otherwise).
#
#   scripts/linux/build.sh
#   scripts/linux/build.sh -DskipTests        # extra arguments are passed to mvn
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/../.."   # the project root
. scripts/linux/externals.sh   # jtools.conf; setup done? (offers it otherwise); the externals first
refuse_while_running "building"
# shellcheck disable=SC2086 # BUILD_GOALS is a list of goals
exec mvn $BUILD_GOALS "$@"
