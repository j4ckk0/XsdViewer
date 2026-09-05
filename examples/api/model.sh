#!/usr/bin/env bash
# The content model tree of a declaration — what a document of it holds — every box open.
#   examples/api/model.sh complexType:PurchaseOrderType samples/purchaseOrder.xsd samples/ext.xsd
# The first file is the one the declaration is read from; the others are the rest of its workspace,
# where the named types it uses may be declared. The request carries the texts: the server keeps nothing.
set -euo pipefail
url=${XSDVIEWER_URL:-http://127.0.0.1:8080}
id=$1; shift
python3 - "$id" "$@" <<'PY' | curl -sS --fail-with-body -X POST -H 'Content-Type: application/json' --data-binary @- "$url/api/model"
import json, sys
id, files = sys.argv[1], sys.argv[2:]
body = {"files": [{"name": f.split("/")[-1], "text": open(f, encoding="utf-8").read()} for f in files], "home": 0, "id": id, "openAll": True}
print(json.dumps(body))
PY
