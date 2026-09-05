#!/usr/bin/env bash
#
# Publish a version on GitHub: creates the Release from the tag and attaches the archives of releases/.
#
#   scripts/release.sh <version>                           # e.g. scripts/release.sh 2.8.0: the notes are CHANGELOG.md's section
#   scripts/release.sh <version> <whats-new.md>            # the notes from a file instead
#   scripts/release.sh --draft <version> [<whats-new.md>]  # a draft, to check on GitHub before publishing
#   scripts/release.sh --dry-run <version> [<whats-new.md>]  # prints the notes, calls nothing
#   scripts/release.sh --body <version> [<whats-new.md>]     # prints the notes alone (the release workflow uses them)
#
# Before: bump the version in pom.xml (the project's <version> only), write the version's section in
# CHANGELOG.md, commit, tag vX.Y.Z, push the tag, run scripts/package.sh (see PUBLISHING.md,
# section 5). The notes hold the "What's new" part only (Markdown, a bullet list); the script adds
# the intro, the downloads table and the SHA-256 checksums. The release workflow on GitHub does the
# same on a pushed tag: this script is the way by hand.
#
# Token: $GITHUB_TOKEN, else ~/.config/github/xsdviewer-release-token (one line, mode 600) — a
# fine-grained token for this repository only, with Contents: read and write. Never echoed.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."

REPO=j4ckk0/XsdViewer
TOKEN_FILE=$HOME/.config/github/xsdviewer-release-token
draft=false; dry=false; body_only=false
while [ $# -gt 0 ]; do
  case "$1" in
    --draft) draft=true; shift ;;
    --dry-run) dry=true; shift ;;
    --body) body_only=true; dry=true; shift ;;
    -*) echo "unknown option $1" >&2; exit 2 ;;
    *) break ;;
  esac
done
[ $# -eq 1 ] || [ $# -eq 2 ] || { sed -n '3,16p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
version=$1; tag=v$version
if [ $# -eq 2 ]; then
  notes=$2
  [ -r "$notes" ] || { echo "notes file not found: $notes" >&2; exit 1; }
else
  # the version's section of CHANGELOG.md, its heading replaced by "What's new"
  notes=$(mktemp)
  trap 'rm -f "$notes"' EXIT
  scripts/changelog-section.py "$version" > "$notes" || exit 1
fi
for tool in curl python3 sha256sum git; do command -v "$tool" >/dev/null || { echo "$tool not found in PATH" >&2; exit 1; }; done

# What is attached: the four archives of scripts/package.sh, for this version.
jar=xsdviewer-$version.jar; zip=xsdviewer-$version-windows.zip; tgz=xsdviewer-$version-linux.tar.gz; mac=xsdviewer-$version-macos.tar.gz
for f in "$jar" "$zip" "$tgz" "$mac"; do
  [ -f "releases/$f" ] || { echo "releases/$f missing - run scripts/package.sh after the version bump" >&2; exit 1; }
done
grep -q "<version>$version</version>" pom.xml || { echo "pom.xml is not at version $version" >&2; exit 1; }
git rev-parse -q --verify "refs/tags/$tag" >/dev/null || { echo "tag $tag does not exist - git tag -a $tag -m \"$version\"" >&2; exit 1; }
if ! $dry && ! git ls-remote --exit-code --tags github "refs/tags/$tag" >/dev/null 2>&1; then
  echo "tag $tag is not on GitHub - git push github $tag" >&2; exit 1
fi

# The bundled runtime's version, from the image's release file (JAVA_VERSION="21.0.12.1").
jre_version=$(grep -h '^JAVA_VERSION=' app/target/jre/*/release 2>/dev/null | head -1 | cut -d'"' -f2)
[ -n "$jre_version" ] || jre_version=21

body=$(
  echo "XsdViewer $version — explore an XML Schema (\`.xsd\`) in the browser: a **Model** view of what a document of a declaration holds, a **Text** view of the source, a **Graph** view of the schema objects and their links (cardinalities, one or two levels, links followed across files), workspaces and comparison."
  echo
  cat "$notes"
  echo
  echo "## Downloads"
  echo
  echo "| File | For |"
  echo "|---|---|"
  echo "| \`$zip\` | Windows x64, no Java needed — unzip and double-click \`XsdViewer.exe\` (a trimmed Temurin runtime is bundled) |"
  echo "| \`$tgz\` | Linux x64, no Java needed — untar and run \`xsdviewer.sh\` (a trimmed Temurin runtime is bundled) |"
  echo "| \`$mac\` | macOS on Apple silicon, no Java needed — untar, \`xattr -dr com.apple.quarantine xsdviewer-$version\` once (a download is quarantined), then \`./xsdviewer.sh\` (a trimmed Temurin runtime is bundled) |"
  echo "| \`$jar\` | Any OS with Java 21 installed ([how to install it](https://github.com/$REPO#installing-java-21)) — \`java -jar $jar [--port 9090] [--keep-alive] [some.xsd]\` |"
  echo
  echo "The bundled runtime is a jlink image of Eclipse Temurin $jre_version (the modules the tool needs), redistributed under the GPLv2 with Classpath Exception (notices in \`jre/legal\`). XsdViewer itself is Apache 2.0."
  echo
  echo "## Checksums (SHA-256)"
  echo
  echo '```'
  (cd releases && sha256sum "$jar" "$zip" "$tgz" "$mac")
  echo '```'
)

if $body_only; then echo "$body"; exit 0; fi
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

# An upload of a large archive sometimes answers nothing (the connection dropped): tried again, a few times.
for f in "$jar" "$zip" "$tgz" "$mac"; do
  case $f in *.jar) type=application/java-archive ;; *.zip) type=application/zip ;; *) type=application/gzip ;; esac
  for attempt in 1 2 3; do
    if curl -sS "${auth[@]}" -H "Content-Type: $type" --data-binary @"releases/$f" \
        "https://uploads.github.com/repos/$REPO/releases/$id/assets?name=$f" \
        | python3 -c 'import json, sys
try:
    r = json.load(sys.stdin)
except ValueError:
    sys.exit("no answer")
if r.get("state") != "uploaded": sys.exit("upload failed: " + r.get("message", str(r)))
print("uploaded", r["name"], r["size"], "bytes")'; then break; fi
    [ "$attempt" -lt 3 ] || { echo "$f: giving up - attach it by hand on the release page" >&2; exit 1; }
    echo "$f: upload attempt $attempt failed, retrying" >&2
    # a half-uploaded asset of that name would block the retry: remove it
    curl -sS "${auth[@]}" "https://api.github.com/repos/$REPO/releases/$id/assets" | python3 -c 'import json, sys
for a in json.load(sys.stdin):
    if a["name"] == sys.argv[1]: print(a["id"])' "$f" | while read -r aid; do
      curl -sS "${auth[@]}" -X DELETE "https://api.github.com/repos/$REPO/releases/assets/$aid" >/dev/null
    done
  done
done
echo "https://github.com/$REPO/releases/tag/$tag"
