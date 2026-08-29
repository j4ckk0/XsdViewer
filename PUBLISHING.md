# Publishing XsdViewer on GitHub

What a public repository needs, what this repository already has, and the steps to publish.
Checked against the tree at version 1.6.0 (August 2026).

## 1. Required

| # | Item | Why | Status here |
|---|---|---|---|
| 1 | **A licence file** (`LICENSE` at the root) | Without one, the code is "all rights reserved": people may read it but not legally use, copy or modify it. GitHub detects the file and shows the licence on the repository page. | **Done.** Apache License 2.0, like the other jtools projects: `LICENSE`, `<licenses>` in `pom.xml`, "Licence" section in `README.md`, and `license-maven-plugin` stamps the Apache header into every Java source at build time. |
| 2 | **Nothing personal or secret in the tree or the history** | Everything ever committed stays visible: passwords, tokens, machine paths, private hostnames. | **Clean.** No secrets, no absolute paths (only `/home/me` in a test), no NAS names. The git-ignored JRE archives were never committed. |
| 3 | **Author identity you accept to show** | Every commit carries an author name and e-mail; GitHub shows both, and the e-mail is harvestable. | All 21 commits are `j4ckk0 <j4ckk0@gmail.com>`. Fine if that e-mail may be public; otherwise switch to GitHub's private address (`<id>+<user>@users.noreply.github.com`, *Settings ▸ Emails ▸ Keep my email addresses private*) **before** pushing, and rewrite the existing commits (`git filter-repo --mailmap`) since history cannot be changed once forked or cloned. The 20 commits made with Claude Code also carry `Co-Authored-By: Claude …` and `Claude-Session: https://claude.ai/code/session_…` trailers; the links are only usable by your account, but they are visible. Keep or strip them now, the same way. |
| 4 | **Attribution of third-party content** | Copying code or data from elsewhere requires respecting its licence. | **Done.** `samples/purchaseOrder.xsd` is adapted from the W3C *XML Schema Part 0: Primer* purchase-order example (W3C Document Licence): its header now carries the source URL and the W3C copyright, and the README's Licence section repeats it. `samples/import/*.xsd` are original. The only dependency, JUnit (EPL-2.0), is test-scoped and not redistributed. |
| 5 | **Licences of what the release archives bundle** | `mvn package -Pdist` ships an Eclipse Temurin JRE (GPLv2 with Classpath Exception). Redistribution is allowed provided its notices travel with it. | **Done.** The `jre/` directory is copied whole (`legal/` and `release` inside) and `README.md ▸ Packaging` says what is bundled and under which licence. The archives themselves stay git-ignored. |
| 6 | **No build artefacts or IDE files** | Keep the repository to sources. | **Done.** `.gitignore` covers `target/`, `.idea/`, `*.iml`, `.vscode/`, Eclipse files and the JRE archives; `.gitattributes` fixes line endings (`*.bat` CRLF, `*.sh` LF). |
| 7 | **A README** | The repository's front page: what it is, how to run it, how to build it. | **Done** (`README.md`); add the licence section and, ideally, a screenshot (`docs/screenshot.png`) — a picture of the graph says more than the first paragraph. |
| 8 | **A buildable tree from a fresh clone** | Anyone must be able to `git clone` then build with only what the README asks for. | **Done.** `mvn package` needs only a JDK 21 and Maven; tests run without network access. `-Pdist` fails with a clear message when the JRE archives are absent (documented). |

## 2. Recommended

| Item | Why | Suggestion |
|---|---|---|
| **Continuous integration** | Proves every push still builds and passes the tests; shows a badge in the README. | `.github/workflows/build.yml`: `actions/checkout`, `actions/setup-java` (Temurin 21, Maven cache), `mvn -B package`. Optionally upload `target/xsdviewer.jar` as a workflow artefact. |
| **Tags and releases** | Users download a version, not a commit. | `git tag -a v1.6.0 -m "1.6.0"` and push the tag; create a GitHub Release from it, attach `xsdviewer.jar` and the two `-Pdist` archives (they are ~50 MB each: fine for Release assets, never for the repository). |
| **`pom.xml` metadata** | Shown by Maven tooling and GitHub's dependency graph. | **Done**: `<url>`, `<inceptionYear>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`, all pointing at `github.com/j4ckk0/XsdViewer` (same identity as the jtools parent pom). |
| **Repository description and topics** | Discoverability. | Description "Web-based viewer for XML Schema files"; topics `xsd`, `xml-schema`, `java`, `visualization`, `graph`. |
| **Security note** | The tool is a local server that reads files on request from the page. | Already in `architecture.md` (binds `127.0.0.1`, only serves directories it has been shown); repeat one sentence in the README so it is seen. A `SECURITY.md` is optional for a tool of this size. |
| **`CHANGELOG.md`** | The git log already records the versions (1.3.0 … 1.6.0); a short file per version is friendlier. | Optional; GitHub Release notes can serve instead. |
| **`CONTRIBUTING.md` / issue templates** | Only useful if you expect contributions. | Optional. |
| **Default branch name** | GitHub creates `main`; this repository uses `master`. Either works. | Keep `master`, or `git branch -m master main` before the first push and set it as default on GitHub. |
| **Branch protection** | Prevents force-pushes to the published branch. | *Settings ▸ Branches* once the repository exists. |

## 3. What not to publish

- `src/main/resources/embedded/jre/*` (JRE archives, ~50 MB each) and anything under `target/` — already git-ignored.
- Workspace files (`*.xsdviewer.json`) and screenshots from real, non-public schemas.
- Local scripts pointing at your own machines (the NAS push script is outside the repository: keep it that way).

## 4. Publishing, step by step

1. ~~Licence, attribution, pom metadata~~ — done (Apache 2.0).
2. Decide on the author e-mail and the commit trailers (item 3). The pom names `j4ckk057@gmail.com`; the commits carry `j4ckk0@gmail.com`. If they must change, rewrite the history now (`git filter-repo`), before anything is pushed.
3. (Optional) add `.github/workflows/build.yml` and `docs/screenshot.png`. Commit.
4. On GitHub: *New repository*, name `XsdViewer`, public, **without** README/licence/.gitignore (the tree has them). Note the URL.
5. Locally:
   ```bash
   git remote add github git@github.com:<user>/XsdViewer.git   # or https://…
   git push -u github master                                    # or main
   git tag -a v1.6.0 -m "1.6.0" && git push github v1.6.0
   ```
   The existing `origin` (the bare repository on the NAS) keeps working; `git push origin` and `git push github` are independent.
6. On GitHub: set the description and topics; *Releases ▸ Draft a new release* from `v1.6.0`, attach `target/xsdviewer.jar`, `target/xsdviewer-1.6.0-windows.zip` and `target/xsdviewer-1.6.0-linux.tar.gz` (built with `./package.sh`), paste the release notes.
7. Check the repository page as a stranger would: licence shown, README readable, Actions green, release downloadable.
