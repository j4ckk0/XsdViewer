# Publishing XsdViewer on GitHub

What a public repository needs, what this repository already has, and the steps to publish.
Checked against the tree at version 2.5.0 (August 2026). **Published on 29–30 August 2026**: <https://github.com/j4ckk0/XsdViewer>, release [v2.5.0](https://github.com/j4ckk0/XsdViewer/releases/tag/v2.5.0).

## 1. Required

| # | Item | Why | Status here |
|---|---|---|---|
| 1 | **A licence file** (`LICENSE` at the root) | Without one, the code is "all rights reserved": people may read it but not legally use, copy or modify it. GitHub detects the file and shows the licence on the repository page. | **Done.** Apache License 2.0, like the other jtools projects: `LICENSE`, `<licenses>` in `pom.xml`, "Licence" section in `README.md`, and `license-maven-plugin` stamps the Apache header into every Java source at build time. |
| 2 | **Nothing personal or secret in the tree or the history** | Everything ever committed stays visible: passwords, tokens, machine paths, private hostnames. | **Clean.** No secrets, no absolute paths (only `/home/me` in a test), no NAS names. The git-ignored JRE archives were never committed. |
| 3 | **Author identity you accept to show** | Every commit carries an author name and e-mail; GitHub shows both, and the e-mail is harvestable. | **Done.** History rewritten (29 August 2026): every commit is `j4ckk0 <j4ckk057@gmail.com>`, the address of the pom, and the repository's `user.email` is set to it. The commits made with Claude Code also carry `Co-Authored-By: Claude …` and `Claude-Session: https://claude.ai/code/session_…` trailers; the links are only usable by your account, but they are visible. Strip them now (same kind of rewrite) if you do not want them shown. |
| 4 | **Attribution of third-party content** | Copying code or data from elsewhere requires respecting its licence. | **Done.** `samples/purchaseOrder.xsd` is adapted from the W3C *XML Schema Part 0: Primer* purchase-order example (W3C Document Licence): its header now carries the source URL and the W3C copyright, and the README's Licence section repeats it. `samples/import/*.xsd` are original. The only dependency, JUnit (EPL-2.0), is test-scoped and not redistributed. |
| 5 | **Licences of what the release archives bundle** | `mvn package -Pdist` ships an Eclipse Temurin JRE (GPLv2 with Classpath Exception). Redistribution is allowed provided its notices travel with it. | **Done.** The `jre/` directory is copied whole (`legal/` and `release` inside) and `README.md ▸ Packaging` says what is bundled and under which licence. The archives themselves stay git-ignored. |
| 6 | **No build artefacts or IDE files** | Keep the repository to sources. | **Done.** `.gitignore` covers `target/`, `.idea/`, `*.iml`, `.vscode/`, Eclipse files and the JRE archives; `.gitattributes` fixes line endings (`*.bat` CRLF, `*.sh` LF). |
| 7 | **A README** | The repository's front page: what it is, how to run it, how to build it. | **Done** (`README.md`); add the licence section and, ideally, a screenshot (`docs/screenshot.png`) — a picture of the graph says more than the first paragraph. |
| 8 | **A buildable tree from a fresh clone** | Anyone must be able to `git clone` then build with only what the README asks for. | **Done.** `mvn package` needs only a JDK 21 and Maven; tests run without network access. `-Pdist` fails with a clear message when the JRE archives are absent (documented). |

## 2. Recommended

| Item | Why | Suggestion |
|---|---|---|
| **Continuous integration** | Proves every push still builds and passes the tests; shows a badge in the README. | `.github/workflows/build.yml`: `actions/checkout`, `actions/setup-java` (Temurin 21, Maven cache), `mvn -B package`. Optionally upload `target/xsdviewer.jar` as a workflow artefact. |
| **Tags and releases** | Users download a version, not a commit. | **Done** (30 August 2026): tag `v2.5.0`, GitHub Release *XsdViewer 2.5.0* with `xsdviewer.jar`, `xsdviewer-2.5.0-windows.zip` and `xsdviewer-2.5.0-linux.tar.gz` (~50 MB each: fine for Release assets, never for the repository) and the SHA-256 checksums in the notes. |
| **`pom.xml` metadata** | Shown by Maven tooling and GitHub's dependency graph. | **Done**: `<url>`, `<inceptionYear>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`, all pointing at `github.com/j4ckk0/XsdViewer` (same identity as the jtools parent pom). |
| **Repository description and topics** | Discoverability. | **Done** (30 August 2026): description "Explore XML Schema (.xsd) files in the browser: …", website field pointing at `releases/latest`, topics `xsd`, `xml-schema`, `xml`, `schema`, `viewer`, `visualization`, `graph`, `java`, `web-application`, `developer-tools`. |
| **Security note** | The tool is a local server that reads files on request from the page. | Already in `architecture.md` (binds `127.0.0.1`, only serves directories it has been shown); repeat one sentence in the README so it is seen. A `SECURITY.md` is optional for a tool of this size. |
| **`CHANGELOG.md`** | The git log already records the versions (1.3.0 … 2.5.0); a short file per version is friendlier. | Optional; GitHub Release notes can serve instead. |
| **`CONTRIBUTING.md` / issue templates** | Only useful if you expect contributions. | Optional. |
| **Default branch name** | GitHub creates `main`; this repository uses `master`. Either works. | Kept `master`; it is the default branch on GitHub. |
| **Branch protection** | Prevents force-pushes to the published branch. | Still to do: *Settings ▸ Branches* on GitHub. |

## 3. What not to publish

- `jre/*` (JRE archives, ~50 MB each) and anything under `target/` — already git-ignored.
- Workspace files (`*.xsdviewer.json`) and screenshots from real, non-public schemas.
- Local scripts pointing at your own machines (the NAS push script is outside the repository: keep it that way).

## 4. Publishing, step by step

1. ~~Licence, attribution, pom metadata~~ — done (Apache 2.0).
2. ~~Author e-mail~~ — done. The commit trailers (item 3) were kept and published as they are.
3. (Optional, still open) add `.github/workflows/build.yml` and `docs/screenshot.png`. Commit.
4. ~~On GitHub: *New repository*~~ — done (29 August 2026): <https://github.com/j4ckk0/XsdViewer>, public, `master` as default branch.
5. ~~Locally: remote, push, tag~~ — done (29 August 2026):
   ```bash
   git remote add github git@github.com:j4ckk0/XsdViewer.git
   git push -u github master
   git tag -a v2.5.0 -m "2.5.0" && git push github v2.5.0
   ```
   `master` now tracks `github/master`. The existing `origin` (the bare repository on the NAS) still holds the history from before the rewrite: the next `git push origin` needs `--force`.
6. ~~Release, description, topics~~ — done (30 August 2026) through the REST API: release *XsdViewer 2.5.0* from `v2.5.0` with the three artefacts of `scripts/package.sh` and the SHA-256 checksums in the notes; description, website and topics as in section 2.
7. ~~Check the repository page as a stranger would~~ — done: licence detected (Apache-2.0), README rendered, release assets downloadable anonymously (jar checksum verified). No Actions yet (step 3).

## 5. Next release

1. Bump the version in `pom.xml`, commit `Version X.Y.Z`, `git tag -a vX.Y.Z -m "X.Y.Z"`, `git push github master vX.Y.Z`.
2. `scripts/package.sh`, then `sha256sum target/xsdviewer.jar target/xsdviewer-X.Y.Z-*` for the notes.
3. *Releases ▸ Draft a new release* from the tag, attach the three files — or `POST /repos/j4ckk0/XsdViewer/releases` then `uploads.github.com/…/assets` with a token.

Token for the API (the SSH key only serves `git push`): a **fine-grained** personal access token with *Repository access* = **Only select repositories → XsdViewer** — the default *Public repositories (read-only)* greys out the permissions and every write answers `403 Resource not accessible` — and *Contents: Read and write* (releases); *Administration: Read and write* is also needed to change the description or topics. Short expiry, revoke it afterwards.
