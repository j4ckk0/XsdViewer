#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
#
# The project's CI, run locally: the build with every test, quiet, the pages' dependencies installed exactly as
# their package-lock.json has them (npm ci); then scripts/linux/ci.local.sh, when the project has checks of its own.
#
#   scripts/linux/ci.sh
#
# Run by .githooks/pre-push before every push; enable the hooks once with:
#   git config core.hooksPath .githooks
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/../.."   # the project root
. scripts/linux/externals.sh   # jtools.conf; setup done? (offers it otherwise); the externals first
refuse_while_running "the build"
# shellcheck disable=SC2086 # BUILD_GOALS is a list of goals
mvn -q $BUILD_GOALS -Dnpm.install=ci
if [ -f scripts/linux/ci.local.sh ]; then bash scripts/linux/ci.local.sh; fi
