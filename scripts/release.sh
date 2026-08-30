#!/usr/bin/env bash
#
# Publish a version on GitHub: creates the Release from the tag and attaches the archives of releases/.
#
#   scripts/release.sh <version> <whats-new.md>            # e.g. scripts/release.sh 2.8.0 notes.md
#   scripts/release.sh --draft <version> <whats-new.md>    # a draft, to check on GitHub before publishing
#   scripts/release.sh --dry-run <version> <whats-new.md>  # prints the notes, calls nothing
#
# Before: bump the version in pom.xml (the project's <version> only), commit, tag vX.Y.Z, push the
# tag, run scripts/package.sh (see PUBLISHING.md, section 5). The notes file holds the "What's new"
# part only (Markdown, a bullet list); the script adds the intro, the downloads table and the
# SHA-256 checksums.
#
# Token: $GITHUB_TOKEN, else ~/.config/github/xsdviewer-release-token (one line, mode 600) — a
# fine-grained token for this repository only, with Contents: read and write. Never echoed.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."

REPO=j4ckk0/XsdViewer
TOKEN_FILE=$HOME/.config/github/xsdviewer-release-token
draft=false; dry=false
while [ $# -gt 0 ]; do
  case "$1" in
    --draft) draft=true; shift ;;
    --dry-run) dry=true; shift ;;
    -*) echo "unknown option $1" >&2; exit 2 ;;
    *) break ;;
  esac
done
[ $# -eq 2 ] || { sed -n '3,15p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
version=$1; notes=$2; tag=v$version
[ -r "$notes" ] || { echo "notes file not found: $notes" >&2; exit 1; }
for tool in curl python3 sha256sum git; do command -v "$tool" >/dev/null || { echo "$tool not found in PATH" >&2; exit 1; }; done

# What is attached: the three archives of scripts/package.sh, for this version.
jar=xsdviewer-$version.jar; zip=xsdviewer-$version-windows.zip; tgz=xsdviewer-$version-linux.tar.gz
for f in "$jar" "$zip" "$tgz"; do
  [ -f "releases/$f" ] || { echo "releases/$f missing - run scripts/package.sh after the version bump" >&2; exit 1; }
done
grep -q "<version>$version</version>" pom.xml || { echo "pom.xml is not at version $version" >&2; exit 1; }
git rev-parse -q --verify "refs/tags/$tag" >/dev/null || { echo "tag $tag does not exist - git tag -a $tag -m \"$version\"" >&2; exit 1; }
if ! $dry && ! git ls-remote --exit-code --tags github "refs/tags/$tag" >/dev/null 2>&1; then
  echo "tag $tag is not on GitHub - git push github $tag" >&2; exit 1
fi

# The bundled JRE, from the archive name (OpenJDK21U-jre_x64_linux_hotspot_21.0.12.1_1.tar.gz -> 21.0.12).
jre_version=$(compgen -G 'jre/*linux*.tar.gz' | head -1 | sed -E 's/.*hotspot_([0-9]+\.[0-9]+\.[0-9]+).*/\1/')
[ -n "$jre_version" ] || jre_version=21

body=$(
  echo "XsdViewer $version — explore an XML Schema (\`.xsd\`) in the browser: a **Graph** view of the schema objects and their links (cardinalities, one or two levels, links followed across files), a **Text** view of the source, workspaces and comparison."
  echo
  cat "$notes"
  echo
  echo "## Downloads"
  echo
  echo "| File | For |"
  echo "|---|---|"
  echo "| \`$zip\` | Windows, no Java needed — unzip and double-click \`XsdViewer.exe\` (bundled Temurin JRE 21) |"
  echo "| \`$tgz\` | Linux x64, no Java needed — untar and run \`xsdviewer.sh\` (bundled Temurin JRE 21) |"
  echo "| \`$jar\` | Any OS with Java 21 installed ([how to install it](https://github.com/$REPO#installing-java-21)) — \`java -jar $jar [--port 9090] [--keep-alive] [some.xsd]\` |"
  echo
  echo "The bundled JRE is Eclipse Temurin $jre_version, redistributed under the GPLv2 with Classpath Exception (notices in \`jre/legal\`). XsdViewer itself is Apache 2.0."
  echo
  echo "## Checksums (SHA-256)"
  echo
  echo '```'
  (cd releases && sha256sum "$jar" "$zip" "$tgz")
  echo '```'
)

if $dry; then echo "$body"; echo; echo "(dry run: release $tag not created)"; exit 0; fi

if [ -z "${GITHUB_TOKEN:-}" ] && [ -r "$TOKEN_FILE" ]; then GITHUB_TOKEN=$(tr -d '[:space:]' < "$TOKEN_FILE"); fi
: "${GITHUB_TOKEN:?no token: set GITHUB_TOKEN or write it to $TOKEN_FILE}"
auth=(-H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28")

json=$(python3 -c 'import json, sys
print(json.dumps({"tag_name": sys.argv[1], "name": "XsdViewer " + sys.argv[2], "body": sys.stdin.read(),
                  "draft": sys.argv[3] == "true", "prerelease": False, "make_latest": "true"}))' "$tag" "$version" "$draft" <<<"$body")
resp=$(curl -sS "${auth[@]}" -X POST "https://api.github.com/repos/$REPO/releases" -d "$json")
id=$(python3 -c 'import json, sys
r = json.loads(sys.stdin.read())
if "id" not in r: sys.exit("release not created: " + r.get("message", str(r)))
print(r["id"])' <<<"$resp")
echo "release $tag created (id $id$($draft && echo ', draft'))"

for f in "$jar" "$zip" "$tgz"; do
  case $f in *.jar) type=application/java-archive ;; *.zip) type=application/zip ;; *) type=application/gzip ;; esac
  curl -sS "${auth[@]}" -H "Content-Type: $type" --data-binary @"releases/$f" \
    "https://uploads.github.com/repos/$REPO/releases/$id/assets?name=$f" \
    | python3 -c 'import json, sys
r = json.load(sys.stdin)
if r.get("state") != "uploaded": sys.exit("upload failed: " + r.get("message", str(r)))
print("uploaded", r["name"], r["size"], "bytes")'
done
echo "https://github.com/$REPO/releases/tag/$tag"
