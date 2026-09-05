# Publishing XsdViewer on GitHub

What a public repository needs, what this repository already has, and the steps to publish.
Checked against the tree at version 2.6.0 (August 2026). **Published on 29–30 August 2026**: <https://github.com/j4ckk0/XsdViewer>; current release [v5.2.0](https://github.com/j4ckk0/XsdViewer/releases/tag/v5.2.0) (first one: v2.5.0).

## 1. Required

| # | Item | Why | Status here |
|---|---|---|---|
| 1 | **A licence file** (`LICENSE` at the root) | Without one, the code is "all rights reserved": people may read it but not legally use, copy or modify it. GitHub detects the file and shows the licence on the repository page. | **Done.** Apache License 2.0, like the other jtools projects: `LICENSE`, `<licenses>` in `pom.xml`, "Licence" section in `README.md`, and `license-maven-plugin` stamps the Apache header into every Java source at build time. |
| 2 | **Nothing personal or secret in the tree or the history** | Everything ever committed stays visible: passwords, tokens, machine paths, private hostnames. | **Clean.** No secrets, no absolute paths (only `/home/me` in a test), no NAS names. The git-ignored JRE archives were never committed. |
| 3 | **Author identity you accept to show** | Every commit carries an author name and e-mail; GitHub shows both, and the e-mail is harvestable. | **Done.** History rewritten (29 August 2026): every commit is `j4ckk0 <j4ckk057@gmail.com>`, the address of the pom, and the repository's `user.email` is set to it. The commits made with Claude Code also carry `Co-Authored-By: Claude …` and `Claude-Session: https://claude.ai/code/session_…` trailers; the links are only usable by your account, but they are visible. Strip them now (same kind of rewrite) if you do not want them shown. |
| 4 | **Attribution of third-party content** | Copying code or data from elsewhere requires respecting its licence. | **Done.** `samples/purchaseOrder.xsd` is adapted from the W3C *XML Schema Part 0: Primer* purchase-order example (W3C Document Licence): its header now carries the source URL and the W3C copyright, and the README's Licence section repeats it. `samples/import/*.xsd` are original. The only dependency, JUnit (EPL-2.0), is test-scoped and not redistributed. |
| 5 | **Licences of what the release archives bundle** | `mvn package -Pdist` ships a jlink image of Eclipse Temurin (GPLv2 with Classpath Exception). Redistribution is allowed provided its notices travel with it. | **Done.** The image keeps `legal/` and `release`, and `README.md ▸ Packaging` says what is bundled and under which licence. The archives themselves stay git-ignored. |
| 6 | **No build artefacts or IDE files** | Keep the repository to sources. | **Done.** `.gitignore` covers `target/`, `.idea/`, `*.iml`, `.vscode/`, Eclipse files and the JRE archives; `.gitattributes` fixes line endings (`*.bat` CRLF, `*.sh` LF). |
| 7 | **A README** | The repository's front page: what it is, how to run it, how to build it. | **Done** (`README.md`), with the licence section and three screenshots (`screenshots/`, graph, text and compare views on the shipped samples) — a picture of the graph says more than the first paragraph. |
| 8 | **A buildable tree from a fresh clone** | Anyone must be able to `git clone` then build with only what the README asks for. | **Done.** `mvn package` needs only a JDK 21 and Maven; tests run without network access. `-Pdist` fails with a clear message when the JRE archives are absent (documented). |

## 2. Recommended

| Item | Why | Suggestion |
|---|---|---|
| **Continuous integration** | Proves every push still builds and passes the tests; shows a badge in the README. | **Done** (30 August 2026): `.github/workflows/build.yml` — `actions/checkout`, `actions/setup-java` (Temurin 21, Maven cache), `mvn -B package` on every push to `master`, every `v*` tag and every pull request; `target/xsdviewer.jar` kept as a workflow artefact; badge at the top of the README. |
| **Tags and releases** | Users download a version, not a commit. | **Done**: one tag `vX.Y.Z` and one GitHub Release per version (v2.5.0, v2.6.0 and v2.7.1 on 30 August 2026; v2.7.0 is a tag without a Release, superseded the same day), each with `xsdviewer-X.Y.Z.jar`, `-windows.zip` and `-linux.tar.gz` from `releases/` (~50 MB each: fine for Release assets, never for the repository) and the SHA-256 checksums in the notes; the routine is section 5. |
| **`pom.xml` metadata** | Shown by Maven tooling and GitHub's dependency graph. | **Done**: `<url>`, `<inceptionYear>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`, all pointing at `github.com/j4ckk0/XsdViewer` (same identity as the jtools parent pom). |
| **Repository description and topics** | Discoverability. | **Done** (30 August 2026): description "Explore XML Schema (.xsd) files in the browser: …", website field pointing at `releases/latest`, topics `xsd`, `xml-schema`, `xml`, `schema`, `viewer`, `visualization`, `graph`, `java`, `web-application`, `developer-tools`. |
| **Security note** | The tool is a local server that reads files on request from the page. | Already in `architecture.md` (binds `127.0.0.1`, only serves directories it has been shown); repeat one sentence in the README so it is seen. A `SECURITY.md` is optional for a tool of this size. |
| **`CHANGELOG.md`** | The git log already records the versions (1.3.0 … 2.6.0); a short file per version is friendlier. | Optional; the GitHub Release notes serve instead (each release lists what changed since the previous one). |
| **`CONTRIBUTING.md` / issue templates** | Only useful if you expect contributions. | Optional. |
| **Default branch name** | GitHub creates `main`; this repository uses `master`. Either works. | Kept `master`; it is the default branch on GitHub. |
| **Branch protection** | Prevents force-pushes to the published branch. | **Done** (30 August 2026), as two rulesets (*Settings ▸ Rules ▸ Rulesets*): *protect master* on `refs/heads/master` and *protect version tags* on `refs/tags/v*`, both blocking deletion and force-push. Nobody else can push anyway: no collaborator is added, other users can only fork and open pull requests. To move a tag or rewrite history on purpose, disable the ruleset first, then enable it again. |

## 3. What not to publish

- `jre/*` (JRE archives, ~50 MB each) and anything under `target/` — already git-ignored.
- Workspace files (`*.xsdviewer.json`) and screenshots from real, non-public schemas.
- Local scripts pointing at your own machines (the NAS push script is outside the repository: keep it that way).

## 4. Publishing, step by step

1. ~~Licence, attribution, pom metadata~~ — done (Apache 2.0).
2. ~~Author e-mail~~ — done. The commit trailers (item 3) were kept and published as they are.
3. ~~CI workflow and screenshots~~ — done (30 August 2026): `.github/workflows/build.yml`, `screenshots/` linked from the README.
4. ~~On GitHub: *New repository*~~ — done (29 August 2026): <https://github.com/j4ckk0/XsdViewer>, public, `master` as default branch.
5. ~~Locally: remote, push, tag~~ — done (29 August 2026):
   ```bash
   git remote add github git@github.com:j4ckk0/XsdViewer.git
   git push -u github master
   git tag -a v2.5.0 -m "2.5.0" && git push github v2.5.0
   ```
   `master` now tracks `github/master`. The existing `origin` (the bare repository on the NAS) still holds the history from before the rewrite: the next `git push origin` needs `--force`.
6. ~~Release, description, topics~~ — done (30 August 2026) through the REST API: release *XsdViewer 2.5.0* from `v2.5.0` with the three artefacts of `scripts/package.sh` and the SHA-256 checksums in the notes; description, website and topics as in section 2.
7. ~~Check the repository page as a stranger would~~ — done: licence detected (Apache-2.0), README rendered, release assets downloadable anonymously (jar checksum verified), Actions green.

## 5. Next release

Followed for v2.6.0, v2.7.1 (30 August 2026) , v2.8.0, v3.0.0, v3.1.0, v3.2.0, v3.3.0 and v3.3.1 (2 September 2026), and v4.0.0, v4.0.1, v4.1.0, v4.2.0, v4.3.0, v4.4.0, v4.5.0, v4.6.0, v4.7.0, v4.8.0 (4 September 2026) and v4.9.0, v4.10.0, v4.11.0, v4.12.0, v4.13.0, v5.0.0 and v5.0.1 (5 September 2026): version bump, tag, `scripts/package.sh`, release through the API — about ten minutes. The parent's `<version>` is what `release.yml` checks against the tag; a plugin in the `dist` profile may carry the same number (launch4j was 2.7.0).

1. Turn the *Unreleased* section of `CHANGELOG.md` into `## X.Y.Z — date` (the release notes come from it), bump the version of the three poms at once — `mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false` (the parent's `<version>` and the two modules' `<parent>` blocks) —, commit `Version X.Y.Z`, `git tag -a vX.Y.Z -m "X.Y.Z"`, `git push github master vX.Y.Z`.
2. The `release` workflow (`.github/workflows/release.yml`) does the rest on the pushed tag: it downloads the Temurin JDKs, runs `mvn package -Pdist` (jlink runtimes for Windows, Linux and macOS), and creates the Release from the changelog section with the archives and their checksums. Check the Actions run, then the release page.
3. By hand instead (the workflow failed, or offline): `scripts/package.sh` with the JDK archives in `jre/`, then `scripts/release.sh X.Y.Z` (`--dry-run` to read the notes first, `--draft` to check the page before publishing).
4. Update the "current release" line at the top of this file.

Token for the API (the SSH key only serves `git push`): a **fine-grained** personal access token with *Repository access* = **Only select repositories → XsdViewer** — the default *Public repositories (read-only)* greys out the permissions and every write answers `403 Resource not accessible` — and *Contents: Read and write* (releases); *Administration: Read and write* is also needed to change the description or topics. Short expiry, revoke it afterwards. Kept in `~/.config/github/xsdviewer-release-token` (mode 600, outside the repository) for `scripts/release.sh` to read, or given as `$GITHUB_TOKEN`.

## 6b. The repository's own page

The description and the topics are what GitHub search and the repository's header show, and they are
not in any file: they live on the repository itself. Changing them needs a token with
*Administration: Read and write* — the release token has *Contents* only, and answers `403 Resource
not accessible by personal access token`. Set them by hand (the ✏️ beside **About**), or with a token
that has it:

    description: Explore XML Schema, WSDL and Schematron files in the browser: what a document of a
    declaration holds, its source, the graph of its links; workspaces, comparison of two declarations
    or two workspaces, validation. Also a Java library (org.jtools:xsdviewer-core) and an HTTP API.
    Self-contained packages, no Java install needed.

    topics: xml, xsd, xml-schema, wsdl, schematron, viewer, graph, visualization, java,
    java-library, api, developer-tools, web-application, validation, maven

## 7. The library on Maven Central

`core` is meant to be published as `org.jtools:xsdviewer-core`; `app` never is. **This is not part of
a release**: section 5 stops at the GitHub release, and a version reaches Central only when it is
asked for on its own — what Central publishes can never be replaced or withdrawn. What it takes, once:

1. **The namespace `org.jtools`.** Maven Central hands out a groupId only to who owns the domain it
   reverses, and jtools.org is ours. On <https://central.sonatype.com>, *Namespaces* ▸ *Add
   Namespace* ▸ `org.jtools`: the portal answers with a verification key, which goes into a **DNS TXT
   record on `jtools.org`** (name `@`, value the key alone), then *Verify Namespace*. DNS takes
   minutes to hours to propagate; `dig +short TXT jtools.org` says when the record is visible. Once
   verified the namespace is ours for good, and every `org.jtools:*` artifact can be published.
2. **An account on <https://central.sonatype.com>**, and a *user token* from it (*View Account* ▸
   *Generate User Token*), kept as the server `central` in `~/.m2/settings.xml`:

       <settings><servers><server>
         <id>central</id><username>…</username><password>…</password>
       </server></servers></settings>

3. **A GPG key** (`gpg --full-generate-key`), its public part sent to a key server the portal reads
   (`gpg --list-secret-keys --keyid-format=long`, then
   `gpg --keyserver keyserver.ubuntu.com --send-keys <id>`); `maven-gpg-plugin` signs with the
   default key, or `-Dgpg.keyname=<id>`. Central refuses an unsigned bundle.

Then, for a version: `mvn -Ppublish -DskipTests -pl core -am deploy` builds core's jar, its sources and its
javadoc, signs the three and uploads the bundle to the portal, where it is validated. Nothing is
released until **Publish** is pressed there (`autoPublish` is off), so a mistake costs nothing.
The version must not be a SNAPSHOT.

Two things learnt doing it the first time (v5.0.0, 5 September 2026):

- `gpg --send-keys` reports success here while the key never leaves: the key-server ports are
  filtered. Upload over HTTPS instead — `gpg --armor --export <id> > pub.asc` then
  `curl --data-urlencode "keytext@pub.asc" https://keyserver.ubuntu.com/pks/add`, which answers with
  the fingerprint it inserted.
- A failed build after `Uploaded bundle successfully` does not mean a failed upload: the plugin polls
  the portal afterwards, and an old one throws on a field the portal has since added. Ask the portal
  itself rather than trusting the exit code:

      curl -X POST -H "Authorization: Bearer $(printf '%s:%s' "$USER_TOKEN" "$PASSWORD" | base64 -w0)" \
        "https://central.sonatype.com/api/v1/publisher/status?id=<deploymentId>"

  `deploymentState: VALIDATED` means it is staged and waiting for **Publish**.

