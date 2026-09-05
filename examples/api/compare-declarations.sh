#!/usr/bin/env bash
# Two declarations compared: their trees with every box marked same / changed / removed / added, the
# counts, and the links only one side has.
#   examples/api/compare-declarations.sh complexType:ProductType samples/compare/v1/product.xsd samples/compare/v2/product.xsd
# Each side carries the schemas of its folder; the named file is the one the declaration is read from.
set -euo pipefail
url=${XSDVIEWER_URL:-http://127.0.0.1:8080}
python3 - "$1" "$2" "$3" <<'PY' | curl -sS --fail-with-body -X POST -H 'Content-Type: application/json' --data-binary @- "$url/api/compare/declarations"
import json, os, sys
id, left, right = sys.argv[1:4]
def side(file):
    folder = os.path.dirname(file) or "."
    names = sorted(n for n in os.listdir(folder) if n.endswith(".xsd"))
    files = [{"name": n, "text": open(os.path.join(folder, n), encoding="utf-8").read()} for n in names]
    return {"files": files, "home": names.index(os.path.basename(file)), "id": id}
print(json.dumps({"left": side(left), "right": side(right)}))
PY
