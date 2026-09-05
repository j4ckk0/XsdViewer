#!/bin/sh
# Run XsdViewer with the bundled runtime (Linux, macOS). Arguments are passed to the tool:
#   ./xsdviewer.sh [--port N] [--host H] [--no-browser] [--keep-alive] [--verbose] [file.xsd]
dir=$(cd "$(dirname "$0")" && pwd)
exec "$dir/jre/bin/java" -jar "$dir/xsdviewer.jar" "$@"
