#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
#
# Stops the program: its servers running on this machine — from scripts/linux/run.sh (<id>-run.jar) or from a distribution
# (<id>.jar). Closing its last page stops one too; this is for the others (--keep-alive, --no-browser). The clean way
# first: SIGTERM, which the program's shutdown hook answers by closing the server; SIGKILL for one still running
# 10 seconds later.
#
#   scripts/linux/stop.sh              # every server of the program
#   scripts/linux/stop.sh --port 9090  # the one started with --port 9090
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/../.."   # the project root

APP_ID= APP_NAME=
while IFS='=' read -r key value; do
  key=${key%$'\r'} value=${value%$'\r'}   # a file written on Windows (CRLF) reads the same
  case "$key" in APP_ID|APP_NAME) printf -v "$key" '%s' "$value" ;; esac
done < jtools.conf
[ -n "$APP_ID" ] || { echo "jtools.conf names no program (APP_ID): there is nothing to stop in this project" >&2; exit 1; }
command -v pgrep >/dev/null || { echo "scripts/linux/stop.sh finds the program with pgrep, which Git Bash has not: on Windows, scripts\\stop.bat" >&2; exit 1; }

port=
case "${1:-}" in
  '') ;;
  --port) port=${2:?--port takes a number} ;;
  *) echo "unknown argument: $1 (try --port N)" >&2; exit 2 ;;
esac

# The port a server was started with: its --port argument, "default" without one (its .ini's).
served_port() {
  local args
  args=$(tr '\0' ' ' < "/proc/$1/cmdline" 2>/dev/null) || return 0
  if [[ $args =~ --port[[:space:]]+([0-9]+) ]]; then echo "${BASH_REMATCH[1]}"; else echo default; fi
}

# anchored on the process's own java: a shell command line that merely names the jar is not a server
servers=()
for pid in $(pgrep -f "^[^[:space:]]*java[^[:space:]]* .*-jar[[:space:]]+[^[:space:]]*$APP_ID(-run)?\\.jar" || true); do
  if [ -z "$port" ] || [ "$(served_port "$pid")" = "$port" ]; then servers+=("$pid"); fi
done
if [ ${#servers[@]} -eq 0 ]; then
  echo "No $APP_NAME server is running${port:+ on port $port}."
  exit 0
fi

for pid in "${servers[@]}"; do
  echo "== stopping $APP_NAME on port $(served_port "$pid") (pid $pid)"
  kill -TERM "$pid" 2>/dev/null || true
done
for _ in $(seq 40); do
  alive=()
  for pid in "${servers[@]}"; do kill -0 "$pid" 2>/dev/null && alive+=("$pid"); done
  [ ${#alive[@]} -eq 0 ] && { echo "== $APP_NAME stopped"; exit 0; }
  sleep 0.25
done
echo "== still running after 10 s: killed (pid ${alive[*]})" >&2
kill -KILL "${alive[@]}" 2>/dev/null || true
