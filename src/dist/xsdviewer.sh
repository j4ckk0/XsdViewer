#!/bin/sh
# Run XsdViewer with the bundled JRE. Arguments are passed to the tool:
#   ./xsdviewer.sh [--port N] [--host H] [--no-browser] [file.xsd]
dir=$(dirname "$(readlink -f "$0")")
exec "$dir/jre/bin/java" -jar "$dir/xsdviewer.jar" "$@"
