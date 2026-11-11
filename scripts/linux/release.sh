#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (jtools sync refreshes it): change it in its devkit/, not here.
#
# Publishes the version the root pom names, once its tag v<version> exists and scripts/linux/package.sh has made its
# archives in releases/: the notes are CHANGELOG.md's "## <version>" section, the archives' SHA-256 added beneath.
#   - with RELEASE_GITHUB=<owner>/<repository> in jtools.conf: a GitHub Release from the tag, pushed there first,
#     every releases/<id>-<version>* file attached. Its token: $GITHUB_TOKEN, else the one line of
#     ~/.config/github/<id>-release-token (mode 600) — a fine-grained token for that repository, Contents: read and
#     write. Never shown.
#   - without it, a local release: releases/<id>-<version>.sha256 and releases/<id>-<version>-notes.md beside them.
#
#   scripts/linux/release.sh               publish
#   scripts/linux/release.sh --draft       a GitHub draft, to read on its page before publishing it there
#   scripts/linux/release.sh --dry-run     the notes printed, and the request a GitHub Release would send written to
#                                          releases/<id>-<version>-release.json; nothing published
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/../.."   # the project root
. scripts/linux/externals.sh   # jtools.conf; setup done? (offers it otherwise)
require_program "release"

draft=false dry=false
for arg in "$@"; do
  case "$arg" in
    --draft) draft=true ;;
    --dry-run) dry=true ;;
    *) echo "unknown option: $arg (--draft, --dry-run)" >&2; exit 2 ;;
  esac
done
for tool in curl sha256sum; do command -v "$tool" >/dev/null || { echo "$tool not found in PATH" >&2; exit 1; }; done

version=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' pom.xml | head -1)
[ -n "$version" ] || version=$(sed '/<parent>/,/<\/parent>/d' pom.xml | sed -n 's:.*<version>\(.*\)</version>.*:\1:p' | head -1)
[ -n "$version" ] || { echo "pom.xml names no version (its <revision>, or its own <version>)" >&2; exit 1; }
tag=v$version base="$APP_ID-$version"

# What is published: every file of releases/ named after this version, but what this script writes there itself
files=()
for f in "releases/$base"-* "releases/$base".*; do
  [ -f "$f" ] || continue
  case "$f" in *.sha256|*-notes.md|*-release.json) continue ;; esac
  files+=("$f")
done
[ ${#files[@]} -gt 0 ] || { echo "no releases/$base*: run scripts/linux/package.sh first" >&2; exit 1; }

# The notes: CHANGELOG.md's section of this version — its "## <version>" line (a date may follow) to the next "## " —,
# then the checksums
[ -f CHANGELOG.md ] || { echo "no CHANGELOG.md: the notes are its \"## $version\" section" >&2; exit 1; }
section=$(tr -d '\r' < CHANGELOG.md | awk -v v="$version" '
  /^## / { if (inside) exit; head = substr($0, 4); if (head == v || index(head, v " ") == 1) { inside = 1; next } }
  inside { print }')
[ -n "$(tr -d '[:space:]' <<< "$section")" ] || { echo "CHANGELOG.md has no \"## $version\" section, or an empty one" >&2; exit 1; }
sums=$(cd releases && sha256sum "${files[@]#releases/}")
notes=$(printf '%s\n\n## Checksums (SHA-256)\n\n```\n%s\n```\n' "$(sed -e '/./,$!d' <<< "$section")" "$sums")

if [ -z "${RELEASE_GITHUB:-}" ]; then
  # A local release: the tag here, the checksums and the notes beside the archives
  if command -v git >/dev/null && [ -d .git ]; then
    git rev-parse -q --verify "refs/tags/$tag" >/dev/null || { echo "no tag $tag: git tag -a $tag -m \"$version\" first" >&2; exit 1; }
  fi
  if $dry; then printf '%s\n' "$notes"; echo "(dry run: no RELEASE_GITHUB in jtools.conf, a local release; nothing written)"; exit 0; fi
  printf '%s\n' "$sums" > "releases/$base.sha256"
  printf '%s\n' "$notes" > "releases/$base-notes.md"
  echo "== released locally: releases/$base.sha256 and releases/$base-notes.md, beside ${#files[@]} files"
  exit 0
fi

# A GitHub Release. The request's JSON, the notes escaped as a JSON string: \ and " escaped, tabs \t, each line ended
# by \n
json_string() { sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/\\t/g' | awk '{ printf "%s\\n", $0 }'; }
request="releases/$base-release.json"
{
  printf '{"tag_name":"%s","name":"%s %s","draft":%s,"body":"' "$tag" "$APP_NAME" "$version" "$draft"
  printf '%s\n' "$notes" | json_string
  printf '"}\n'
} > "$request"
if $dry; then printf '%s\n' "$notes"; echo "(dry run: the request for $RELEASE_GITHUB is in $request; nothing published)"; exit 0; fi

token_file="$HOME/.config/github/$APP_ID-release-token"
if [ -z "${GITHUB_TOKEN:-}" ] && [ -r "$token_file" ]; then GITHUB_TOKEN=$(head -1 "$token_file" | tr -d '\r\n'); fi
[ -n "${GITHUB_TOKEN:-}" ] || { rm -f "$request"; echo "no token: set GITHUB_TOKEN, or write it to $token_file (one line, mode 600)" >&2; exit 1; }
api=https://api.github.com/repos/$RELEASE_GITHUB
answer=$(mktemp)
trap 'rm -f "$answer" "$request"' EXIT
# the headers through a file of curl's own (-H @-): the token never on a command line, where ps would show it
call() { printf 'Authorization: Bearer %s\nAccept: application/vnd.github+json\n' "$GITHUB_TOKEN" | curl -sS -H @- -o "$answer" -w '%{http_code}' "$@"; }

# GitHub would make a tag that is not there, from the default branch: the tag, pushed, is asked for first
code=$(call "$api/git/ref/tags/$tag")
case "$code" in
  200) ;;
  401|403) echo "github.com refuses the token (HTTP $code): expired, or without Contents: read and write on $RELEASE_GITHUB" >&2; exit 1 ;;
  *) echo "tag $tag is not on github.com/$RELEASE_GITHUB (HTTP $code): push it first" >&2; exit 1 ;;
esac
code=$(call -X POST "$api/releases" --data-binary "@$request")
[ "$code" = 201 ] || { echo "release not created (HTTP $code): $(head -c 300 "$answer")" >&2; exit 1; }
id=$(grep -o '"id": *[0-9]*' "$answer" | head -1 | grep -o '[0-9]*$')
link=$(grep -o '"html_url": *"[^"]*"' "$answer" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
echo "== release $tag created on github.com/$RELEASE_GITHUB$($draft && echo ', a draft')"
for f in "${files[@]}"; do
  name=${f#releases/}
  code=$(call -X POST -H "Content-Type: application/octet-stream" --data-binary "@$f" \
    "https://uploads.github.com/repos/$RELEASE_GITHUB/releases/$id/assets?name=$name")
  [ "$code" = 201 ] || { echo "$name not uploaded (HTTP $code): $(head -c 300 "$answer")" >&2; exit 1; }
  echo "   uploaded: $name"
done
echo "== $link"
