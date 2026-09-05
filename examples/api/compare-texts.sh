#!/usr/bin/env bash
# Two files compared line by line: the lines of each with their numbers, the edit script, whether only blocks moved.
#   examples/api/compare-texts.sh samples/compare/v1/product.xsd samples/compare/v2/product.xsd
# businessOnly leaves out comments, annotations and the wiring tags (the XML declaration, xs:schema, xs:import, xs:include).
set -euo pipefail
url=${XSDVIEWER_URL:-http://127.0.0.1:8080}
python3 - "$1" "$2" <<'PY' | curl -sS --fail-with-body -X POST -H 'Content-Type: application/json' --data-binary @- "$url/api/compare/texts"
import json, sys
left, right = (open(f, encoding="utf-8").read() for f in sys.argv[1:3])
print(json.dumps({"left": left, "right": right, "businessOnly": True}))
PY
