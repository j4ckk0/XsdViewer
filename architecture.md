# XsdViewer – architecture

XsdViewer is a two-tier tool: a small **Java server** that parses XML Schema files into a
graph model, and a **browser client** that renders that model as an interactive graph and as
highlighted source text. Everything ships in one jar; the only runtime requirement is a JDK 21.

```
┌───────────────────────────── browser ──────────────────────────────┐
│  index.html / style.css / app.js                                    │
│  File ▸ Open / drag-and-drop ──► fetch POST /api/parse ──► model    │
│  Graph view (SVG ego-graph) · Text view · sidebar · details panel   │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ HTTP (localhost)
┌──────────────────────────────┴─────────────────────────────────────┐
│  Main            com.sun.net.httpserver – static files + /api/*     │
│  XsdParser       XSD text ─► Model   (DOM walk + SAX line index)    │
│  Model / Json    nodes, edges, imports ─► JSON                      │
└────────────────────────────────────────────────────────────────────┘
```

Design choices that shape everything else:

- **Parsing on the server, in Java.** The requirement was a Java tool; the browser is only
  a display. The client never interprets XSD itself.
- **No runtime dependency.** Both tiers use only what the JDK and the browser provide:
  `java.xml` and `jdk.httpserver` on one side, vanilla DOM/SVG on the other. There is no
  build step for the web assets and no framework to upgrade.
- **The file stays in the browser.** The client sends the schema text to the server and
  keeps its own copy for the text view; the server holds no state between requests (except
  the optional file given on the command line).
- **Single file, level-1 links.** The model describes one XSD: its global declarations and
  the direct references between them. Imports/includes are reported, not followed.

## Modules

### Server – `src/main/java/org/jtools/xsdviewer/`

| Class | Role |
|---|---|
| `Main` | Entry point and HTTP layer. Parses the command line (`--port`, `--host`, `--no-browser`, optional `file.xsd`), starts a `com.sun.net.httpserver.HttpServer` bound to `127.0.0.1:8080` by default, serves the web assets from the classpath (`/web/*`), exposes the two API endpoints and opens the browser (`java.awt.Desktop`, falling back to `xdg-open`). Requests are handled on virtual threads (`Executors.newVirtualThreadPerTaskExecutor`). |
| `XsdParser` | The only class that knows XSD. Turns the schema text into a `Model` in three passes (see below). Uses the JDK DOM parser for the structure and a SAX pass to locate each global declaration's start tag. |
| `Model` | Plain data: `Node`, `Edge`, `Import` records, the `targetNamespace`, and `toJson()`. `nodes` is a `LinkedHashMap` (declaration order is kept), `edges` a `LinkedHashSet` (parallel identical edges collapse). |
| `Json` | String escaping for the hand-written JSON writer. The model is flat enough that a JSON library would be the only dependency of the project, so it was left out. |

#### Parsing passes (`XsdParser`)

1. **Declarations → nodes.** Every direct child of `xs:schema` that is an `element`,
   `complexType`, `simpleType`, `group`, `attributeGroup` or `attribute` with a `name`
   becomes a node with id `kind:name`. `xs:import` / `xs:include` / `xs:redefine` are
   collected as `Import`s. The first `xs:annotation/xs:documentation` becomes the node's `doc`.
2. **References → edges.** Each declaration's subtree is walked (anonymous nested types
   included) and every reference is recorded as an edge *from the global declaration*:
   `type=`, `ref=`, `base=` (extension/restriction), `itemType=`, `memberTypes=`,
   `substitutionGroup=`, `group ref`, `attributeGroup ref`. The label says what the reference
   is (`type`, `items` for a nested element, `attribute partNum`, `extends`, `restricts`, `list of`…).
   Names in the XML Schema namespace are resolved immediately to `builtin:X` nodes (unless
   the file itself declares `X`, which happens in schemas using the XSD namespace as default);
   all other targets are kept as `type:X` / `element:X` / … for the next pass.
3. **Resolution.** `type:X` becomes `complexType:X` or `simpleType:X` when declared; any
   target still unknown gets an `external` placeholder node so the graph never has dangling
   edges.

Line numbers come from a separate SAX pass: the SAX `Locator` gives the position of the *end*
of a start tag, so the parser walks back in the text to the `<` to report the line where the
declaration opens, even when its attributes span several lines.

Both XML parsers are configured with secure processing and external entities / DTD loading
disabled: the input is an arbitrary user file.

### Web client – `src/main/resources/web/`

Three static files, no build step, no framework.

| File | Role |
|---|---|
| `index.html` | Page skeleton: top bar (File menu, view tabs, PNG export, built-in toggle), document tab bar, sidebar (schema info, search, object list), main area with the three views (empty / graph / text), details panel, drop overlay, toast. |
| `style.css` | Layout (flexbox: sidebar – main – details), the colour per kind of object (one CSS variable each, reused by sidebar dots, legend, badges and SVG strokes), text-view syntax colours. |
| `app.js` | All behaviour. Organised in sections: **state** (one object per document tab: the model plus derived indexes `outEdges`, `inEdges`, `lineToNode`, the selection, history, view, filter and scroll positions), **loading** (read the `File`, `POST /api/parse`, index the answer), **document tabs**, **selection** (`select(id)` drives every view, keeps the back history), **external declarations** (following a link into another file), **sidebar**, **graph**, **details**, **text view**, **PNG export**, **UI wiring**. |

`state` always points at the active tab's object (`tabs[]` holds them all), so every render
function reads "the current document" without knowing about tabs; `activateTab()` swaps the
pointer and calls `renderAll()`, which redraws the page from that state. Rendering is
"re-render from state": each `select()` rebuilds the SVG, the details panel and the sidebar
highlight from `state`. The text view is rendered once per file or tab switch (it can be
thousands of lines); selection just toggles a highlight class.

#### Following links into other files

An `external` node carries the namespace it was referenced in (`ns`). `followExternal()`
first searches the open tabs for a non-external node with the same kind, name and namespace
(a `type:X` placeholder matches `complexType:X` or `simpleType:X`; a declaration with an
empty namespace matches too, for chameleon includes). Failing that it lists the
`schemaLocation`s of the current file that can hold the namespace (`xs:import` with that
namespace; `xs:include` / `xs:redefine` when it is the file's own namespace) and walks them
breadth-first, opening each file found in a new tab (a file already open in a tab is reused,
its own imports / includes still followed) and following that file's own imports / includes,
with a visited set. `resolveLocation()` finds a location, in order:

1. in the `library` – the `File`s of the folders opened with File ▸ Open folder…
   (`<input webkitdirectory>`) or dropped on the window (`webkitGetAsEntry()` + recursive
   `readEntries`), keyed by relative path; the location is resolved against the folder of the
   referencing file when it came from the library, else matched by path suffix;
2. through `GET /api/open`, which tries the location relative to the referencing file's
   directory (when it is a file the server served), then to the directories of all served
   files, then to the working directory.

Files opened from the browser come without their folder, so `loadInto()` starts
`POST /api/locate` in the background: the server looks for a file with that name and the same
content (BOM and CRLF ignored) under the directories it knows and its working directory
(bounded walk, hidden directories skipped) and returns its path, which then makes `/api/open`
work for the file's imports. Only when all of that fails does `askForFile()` open the file
chooser with a toast naming the wanted file; `pendingJump` remembers what was wanted and
`checkPendingJump()` completes the jump when a file declaring it is loaded.

#### PNG export

Graph: the current SVG is cloned, cropped to its `getBBox()` plus a margin, given a white
background and a copy of the page's stylesheet (so classes and CSS variables resolve in a
standalone document), serialised, loaded into an `Image` and drawn on a canvas at 2×. Text:
the highlighted lines are painted directly on a canvas with the computed colours of each
token class (a very long file is cut to the lines around the current scroll position, to stay
within canvas size limits). `canvas.toBlob()` is then saved through a download link.

#### Graph view

An *ego graph* of the selected node: the node in the centre, every neighbour that it links to
in a column on the right, every neighbour that links to it in a column on the left. Parallel
edges to one neighbour are merged into one line whose label lists the reasons
(`shipTo, billTo`). With the **2 levels** toggle (remembered in `localStorage`) two more
columns show, for every level-1 target its own targets, and for every level-1 user its own
users, as trees: a level-1 node spans as many rows as it has children and sits in the middle
of them; a node reached by several parents is drawn once per parent. The other open tabs
take part: a level-1 `external` target declared in another tab (`findInTabs()`) is drawn
with its real kind and file name and expanded from that tab's model, and nodes of other tabs
that reference the centre or a level-1 user through an external placeholder
(`usersInOtherTabs()`) appear on the left. Nodes of another tab carry `data-tab`, and
clicking one activates that tab before selecting. A self-reference (recursive type) is drawn as a loop above
the centre. The SVG is generated as a string and inserted with `innerHTML`; there is no layout
library because the layout is two columns and a cubic Bézier per edge. Clicking a node calls
`select()` and pushes the previous centre on the history stack.

#### Text view

`highlightXml()` is a small tokenizer (comments, processing instructions, CDATA, tag names,
attributes, values) that emits one `<span>` per token and never lets a span cross a newline,
so the output can be split into lines and each line wrapped with its number. Lines where a
global declaration starts carry the node id; their line number is clickable and selects the
node. The selected node's line is highlighted and scrolled into view.

## HTTP interface

| Method & path | Request | Response |
|---|---|---|
| `GET /`, `/app.js`, `/style.css` | – | the static asset (classpath `web/`, `Cache-Control: no-cache`). Paths are restricted to `/[A-Za-z0-9._-]+`. |
| `POST /api/parse` | body: the XSD text (UTF-8) | `200` + the JSON model, or `400` + `{"error": "…"}` (not XML, root not `xs:schema`, …). |
| `GET /api/initial` | – | `200` + `{"name", "path", "text"}` of the file given on the command line, `404` otherwise. The page calls it once at load. |
| `POST /api/quit` | – | `200` + `{"ok":true}`, then the server stops and the process exits (File ▸ Quit). |
| `GET /api/open?base=…&location=…` | query: `base` = server path of the referencing file (may be empty), `location` = its `schemaLocation` | `200` + `{"name", "path", "text"}` of `location` resolved against `base`'s directory (if `base` is a file the server already served), else against the directories of all served files, else against the working directory; `400` for a remote location (`://`), `404` if not found. |
| `POST /api/locate?name=…` | body: the text of a file opened in the browser | `200` + `{"path"}` of a file with that name and content under the served files' directories or the working directory (depth ≤ 8, ≤ 50 000 entries, hidden directories skipped), `404` otherwise. |

The server binds to `127.0.0.1` unless `--host` says otherwise: it is a local tool, not a
service, and it parses whatever is posted to it.

### JSON model

```json
{
  "targetNamespace": "http://example.com/po",
  "imports": [ { "tag": "import", "namespace": "…", "schemaLocation": "…" } ],
  "nodes":   [ { "id": "complexType:USAddress", "kind": "complexType", "name": "USAddress",
                 "ns": "http://example.com/po", "line": 36, "doc": "…" } ],
  "edges":   [ { "from": "complexType:PurchaseOrderType", "to": "complexType:USAddress",
                 "label": "shipTo" } ]
}
```

`kind` is one of `element`, `complexType`, `simpleType`, `group`, `attributeGroup`,
`attribute`, `builtin`, `external`. `ns` is the target namespace for a declaration, the
referenced namespace for an `external` placeholder (used to find the file declaring it), the
XSD namespace for a `builtin`. `line` is 1-based, `0` when the node has no declaration in the
file.

## Libraries and tooling

Runtime – all part of the JDK (21):

| Library / API | Module | Used for |
|---|---|---|
| `com.sun.net.httpserver` | `jdk.httpserver` | the HTTP server, static files and API routing |
| `javax.xml.parsers` DOM (`DocumentBuilder`) | `java.xml` | walking the schema structure |
| `javax.xml.parsers` SAX (`SAXParser`, `Locator`) | `java.xml` | line numbers of the global declarations |
| `java.awt.Desktop` | `java.desktop` | opening the browser at start-up (optional, guarded) |
| virtual threads (`Executors.newVirtualThreadPerTaskExecutor`) | `java.base` | one thread per request |

Browser side: plain ES2020 JavaScript, DOM, SVG, `fetch`, the File and Drag-and-Drop APIs.
No external script or stylesheet is loaded.

Build and test:

| Tool | Version | Role |
|---|---|---|
| Maven | 3.9 | build; `mvn package` produces `target/xsdviewer.jar` |
| maven-compiler-plugin | 3.13.0 | `--release 21` |
| maven-jar-plugin | 3.4.1 | sets `Main-Class: org.jtools.xsdviewer.Main` (no shading needed: no dependencies) |
| maven-surefire-plugin | 3.2.5 | runs the tests |
| JUnit Jupiter | 5.8.2 (test scope) | `XsdParserTest`, run against `samples/purchaseOrder.xsd` |
| `run.sh` / `run.bat` | – | rebuilds the jar when sources are newer, then runs it (Linux/macOS, Windows) |
| `build.sh` / `build.bat` | – | `mvn package` |
| `package.sh` / `package.bat` | – | `mvn package -Pdist`, after checking the JRE archives are present |
| maven-antrun-plugin | 3.1.0 | `dist` profile only: unpacks the JRE archives into `target/jre/{windows,linux}` |
| maven-assembly-plugin | 3.7.1 | `dist` profile only: `src/assembly/{windows,linux}.xml` → zip / tar.gz with the JRE, the jar, a launcher, `samples/` and `README.md` |

`mvn package -Pdist` produces `target/xsdviewer-<version>-windows.zip` and
`-linux.tar.gz`. The JRE archives under `src/main/resources/embedded/jre/` are
git-ignored (downloaded by hand, see README) and excluded from the jar's resources;
the antrun step fails when one is missing. The Linux descriptor restores the
executable bits on `jre/bin/*`, `lib/jspawnhelper` and `lib/jexec` that Ant's
`untar` drops.

## Repository layout

```
XsdViewer/
├── pom.xml
├── run.sh, run.bat               build if needed + run
├── build.sh, build.bat           mvn package
├── package.sh, package.bat       mvn package -Pdist  (zip / tar.gz with bundled JRE)
├── README.md                     usage
├── architecture.md               this file
├── samples/purchaseOrder.xsd     small schema exercising every kind of link
├── samples/import/               order.xsd + the files it imports / includes (link following)
└── src/
    ├── assembly/                        windows.xml, linux.xml (dist profile)
    ├── dist/                            xsdviewer.bat, xsdviewer.sh launchers
    ├── main/java/org/jtools/xsdviewer/   Main, XsdParser, Model, Json
    ├── main/resources/web/              index.html, app.js, style.css
    ├── main/resources/embedded/jre/     JRE archives bundled by the dist profile (git-ignored)
    └── test/java/org/jtools/xsdviewer/   XsdParserTest
```

## Extension points

- **Following imports/includes.** `XsdParser` works on one text. Multi-file support would
  need a resolver that reads `schemaLocation` relative to the opened file — which the
  browser cannot provide from a dropped file; the natural route is a server-side "open
  directory/path" mode, or letting the user drop several files that the client merges by
  target namespace before parsing.
- **More link kinds** are a new `case` in `XsdParser.collect()` plus a label; the client
  needs nothing (labels are free text).
- **Other graph layouts** only touch `renderGraph()` in `app.js`; the rest of the client
  depends on `select()` and `state`, not on how the SVG is built.
- **Node details** (e.g. facets, cardinalities) would extend `Model.Node` and `toJson()`,
  and `renderDetails()` on the client.
