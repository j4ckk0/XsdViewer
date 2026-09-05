#!/usr/bin/env bash
# The schemas of two folders paired by name, each pair with its status: same, moved, different, only-left, only-right.
#   examples/api/compare-workspaces.sh samples/compare/v1 samples/compare/v2
set -euo pipefail
url=${XSDVIEWER_URL:-http://127.0.0.1:8080}
python3 - "$1" "$2" <<'PY' | curl -sS --fail-with-body -X POST -H 'Content-Type: application/json' --data-binary @- "$url/api/compare/workspaces"
import json, os, sys
def files(folder):
    return [{"name": n, "text": open(os.path.join(folder, n), encoding="utf-8").read()} for n in sorted(os.listdir(folder)) if n.endswith(".xsd")]
print(json.dumps({"left": files(sys.argv[1]), "right": files(sys.argv[2]), "businessOnly": True}))
PY
