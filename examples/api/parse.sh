#!/usr/bin/env bash
# The graph of a schema file, as JSON: its declarations, their links, the content model of each.
#   examples/api/parse.sh samples/purchaseOrder.xsd
# The server must be running: scripts/run.sh --no-browser --keep-alive   (XSDVIEWER_URL overrides http://127.0.0.1:8080)
set -euo pipefail
url=${XSDVIEWER_URL:-http://127.0.0.1:8080}
curl -sS --fail-with-body -X POST -H 'Content-Type: text/plain; charset=utf-8' --data-binary @"$1" "$url/api/parse"
