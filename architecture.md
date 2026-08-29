# XsdViewer – architecture

XsdViewer is a two-tier tool: a small **Java server** that parses XML Schema files into a
graph model, and a **browser client** that renders that model as an interactive graph and as
highlighted source text. Everything ships in one jar; the only runtime requirement is a JDK 21.

```
┌───────────────────────────── browser ──────────────────────────────┐
│  index.html / style.css / js/*.js (ES modules) / i18n/<lang>.json   │
│  File ▸ Open / drag-and-drop ──► fetch POST /api/parse ──► model    │
│  Graph view (SVG ego-graph) · Text view · sidebar · details panel   │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ HTTP (localhost)
┌──────────────────────────────┴─────────────────────────────────────┐
│  server/         com.sun.net.httpserver – static files + /api/*     │
│  schema/         XSD text ─► SchemaGraph (DOM walk + SAX line index)│
│  json/           nodes, edges, imports ─► JSON                      │
│  Messages        server texts, messages_<lang>.properties           │
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
  the optional file given on the command line and the list of files it has served).
- **The server knows the disk, the browser does not.** A browser hides where a chosen file
  is; the server, on the same machine, opens native file dialogs (`java.awt.FileDialog`) for
  File ▸ Open… and the workspace commands, so that files come with their location, links
  can be followed relative to it, and a workspace can record it.
- **Single file, level-1 links.** The model describes one XSD: its global declarations and
  the direct references between them. Imports/includes are reported, not followed.

## Modules

### Server – `src/main/java/org/jtools/xsdviewer/`

Each class does one thing and is named for it; the packages follow the tiers of the diagram.

| Class | Role |
|---|---|
| `XsdViewerApplication` | Entry point: reads the `CommandLineOptions`, checks the initial file, starts the `XsdViewerServer`, prints the URL and opens the browser. |
| `CommandLineOptions` | Record of the options (`--port`, `--host`, `--no-browser`, `-h`, optional `file.xsd`) and their parser. |
| `BrowserLauncher` | Opens a URL with `java.awt.Desktop`, falling back to `xdg-open`. |
| `Messages`, `MessageKey` | The texts the server prints or sends to the page (console, API errors, generated documentation of placeholder nodes), read from `messages.properties` (English) / `messages_fr.properties` for the JVM locale; `MessageKey` holds the keys. |
| `schema.XsdParser` | The only class that knows XSD. Turns the schema text into a `SchemaGraph` in three passes (see below) with the JDK DOM parser. |
| `schema.DeclarationLineIndex` | SAX pass locating the start tag of each global declaration (line numbers). |
| `schema.SecureXmlFactories` | DOM / SAX factories with external entities and DTD loading disabled. |
| `schema.SchemaGraph` | Plain data: `Node`, `Edge`, `Import` records, the `targetNamespace`, `nodeId(kind, name)`. `nodes` is a `LinkedHashMap` (declaration order is kept), `edges` a `LinkedHashSet` (parallel identical edges collapse). |
| `schema.NodeKind`, `schema.LinkLabel`, `schema.XsdVocabulary` | The constants of the model: the kinds of node, the edge labels, and the XSD namespace / element / attribute names the parser reads. |
| `schema.SchemaGraphJsonWriter` | `SchemaGraph` → JSON (keys in `json.JsonKey`). |
| `json.JsonWriter`, `json.JsonStrings`, `json.JsonKey` | A minimal streaming JSON writer and the string escaping. The model is flat enough that a JSON library would be the only dependency of the project, so it was left out. `JsonKey` is the API contract, mirrored by the client. |
| `server.XsdViewerServer` | Starts a `com.sun.net.httpserver.HttpServer` bound to `127.0.0.1:8080` by default and maps each path of `ApiPath` to its handler. Requests are handled on virtual threads (`Executors.newVirtualThreadPerTaskExecutor`). |
| `server.ParseSchemaHandler`, `InitialFileHandler`, `OpenSchemaLocationHandler`, `LocateSchemaFileHandler`, `QuitHandler`, `StaticResourceHandler` | One handler per path of the HTTP interface below. |
| `server.ServedSchemaFiles` | The files the server has read and handed to the page: the only directories `/api/open` and `/api/locate` look into. |
| `server.FileDialogs` | The native open / save dialogs (`java.awt.FileDialog`, one at a time, on a platform thread); `available()` is false when the JVM is headless. |
| `server.ChooseFilesHandler`, `SaveWorkspaceHandler`, `OpenWorkspaceHandler`, `CapabilitiesHandler`, `WorkspaceResponse` | The dialog-backed endpoints and the answer describing an opened workspace (its files read, the missing ones listed). |
| `workspace.Workspace` | The workspace record (file paths + active tab) and its `*.xsdviewer.json` form: paths relative to the workspace file when they share its root. |
| `json.JsonReader` | Minimal parser (objects, arrays, strings, numbers, booleans, null) for workspace files and the page's requests. |
| `server.SchemaFileFinder` | Bounded walk looking for a file with a given name and content (`/api/locate`). |
| `server.HttpResponses`, `QueryString`, `ContentType`, `HttpMethod`, `HttpStatus` | Reading the request, writing text / JSON / error answers, query parameters, MIME types by extension. |

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

Static files, no build step, no framework: `index.html`, `style.css`, ES modules under `js/`
(loaded by `<script type="module" src="js/app.js">`) and the texts under `i18n/`.

| File | Role |
|---|---|
| `index.html` | Page skeleton: top bar (File menu, view tabs, PNG export, built-in toggle), document tab bar, sidebar (schema info, search, object list), main area with the three views (empty / graph / text), details panel, drop overlay, toast. Every label carries a `data-i18n` / `data-i18n-title` / `data-i18n-placeholder` key. |
| `style.css` | Layout (flexbox: sidebar – main – details), the colour per kind of object (one CSS variable each, reused by sidebar dots, legend, badges and SVG strokes), text-view syntax colours. |
| `i18n/en.json`, `i18n/fr.json` | The texts of the page, one flat JSON file per language (same keys in each, checked by `TranslationsTest`). |
| `js/app.js` | Start-up: `await initI18n()`, `wireEvents()`, `renderPage()`, `loadInitialFile()`. |
| `js/constants.js`, `js/dom.js`, `js/message-keys.js` | The strings of the client: the API contract and vocabulary shared with the server (kinds, paths, storage keys); element ids, CSS classes and data attributes; the keys of the texts. |
| `js/i18n.js`, `js/language-selector.js`, `js/kind-labels.js` | Language choice (`?lang=`, remembered choice, else the machine's locale, else English), loading of `i18n/<lang>.json`, `t(key, …args)`, `plural()`, `translate(root)` for the `data-i18n*` bindings, `setLanguage()`; the drop-list; labels of node kinds. |
| `js/state.js` | `newTabState()` (one object per document tab: the model plus derived indexes `outEdges`, `inEdges`, `lineToNode`, the selection, history, view, filter and scroll positions, and its `workspace`), `newWorkspaceState()` (a workspace: `path` once saved / opened, `number` until then) and `session` (the workspaces, their tabs — one flat list grouped in workspace order —, the active tab, the pending jump, the folder library). |
| `js/api.js` | The `fetch` calls to `/api/*`, one function per path. |
| `js/schema-index.js`, `js/declarations.js` | Indexing a parsed schema into a tab; finding declarations across the tabs of a workspace (`findIn`, `findInTabs`, `usersInOtherTabs`, `locationsFor`) — never across workspaces. |
| `js/library.js`, `js/schema-loader.js` | The folder library (Open folder… / dropped folders); `loadInto(tab, …)` (parse through the server, index, locate) and `resolveLocation()` (library, then server; `strict` = relative to the file only). |
| `js/linked-schemas.js` | `openLinkedSchemas(tab)`: opens in background tabs the schemas linked (strictly relative to the file) from a file whose location is known, recursively, serialised and capped. |
| `js/tabs.js`, `js/page.js` | Workspaces and their tabs (`newWorkspace`, `newTab(ws)`, `tabsOf(ws)`, activate / close a tab or a workspace, `closeAllTabs`, the workspace bar with one chip per workspace and the tab bar with the active workspace's tabs); `renderPage()` redraws everything from the active tab, `showView()` switches graph / text. |
| `js/navigation.js` | `select(id)` drives every view and keeps the back history; `followExternal()` follows a link into another file (see below). |
| `js/about.js` | Help ▸ About: a `<dialog>` with the version and Java runtime reported by the server, the licence and the project page. |
| `js/compare.js`, `js/schema-diff.js`, `js/diff.js` | Workspace comparison: the selection (Ctrl+click on chips, `session.compareSelection`), the view (`session.compare`; files paired by name, statuses, expandable rows), the model diff (declared nodes and edges — cardinality included — on one side only) and the LCS line diff (common start / end trimmed, capped at 9 M cells). |
| `js/file-actions.js`, `js/events.js` | The File menu actions (open through the server's dialog or the browser's, open a folder as a workspace named after it, new / open / save / close workspace — an opened workspace is its own group of tabs, saving writes the active workspace —, close, quit, initial file or workspace); wiring of every control, key and drop to the actions. |
| `js/sidebar.js`, `js/graph.js`, `js/details.js`, `js/text-view.js`, `js/xml-highlighter.js`, `js/png-export.js` | One module per view: schema header (foldable) and object list, SVG ego-graph, details panel (collapsible to a strip), source text, its tokenizer, the PNG export. Folded states are remembered in `localStorage`. |

`session.active` always points at the active tab's object (`session.tabs` holds them all), so
every render function reads "the current document" without knowing about tabs;
`activateTab()` swaps the pointer and the caller redraws with `renderPage()`. Rendering is
"re-render from state": each `select()` rebuilds the SVG, the details panel and the sidebar
highlight from the tab. The text view is rendered once per file or tab switch (it can be
thousands of lines); selection just toggles a highlight class. The module graph has no cycle:
views depend on the state, `navigation` on the views, `file-actions` / `events` on both.

#### Texts and constants

Two rules apply to both tiers:

- **No hard-coded strings in the logic.** Names that carry meaning live in constants classes /
  modules: the API contract (`ApiPath` ↔ `API`, `JsonKey`, `NodeKind` ↔ `NODE_KIND`,
  `LinkLabel`), the XSD vocabulary (`XsdVocabulary`), element ids / CSS classes / data
  attributes (`dom.js`), storage keys and MIME types (`constants.js`). Edge labels
  (`type`, `extends`, `list of`…) are part of the model, not user-interface text: they are
  constants, not translated.
- **User-visible texts come from resource files, one per language.** Server side,
  `Messages.get(MessageKey.X, args…)` reads `messages.properties` (English, the base file) or
  `messages_<language>.properties` (`MessageFormat` patterns) for the locale of the request
  being handled — the page sends its language in `Accept-Language`, `XsdViewerServer`
  applies it to `Messages` for the duration of the request (a `ThreadLocal`, one virtual
  thread per request); console messages use the JVM locale. A language without a file gets
  the base file, never the JVM's language. Client side, `t(MSG.X, args…)` reads
  `i18n/<language>.json`, chosen from `?lang=`, else the choice remembered from the top-bar
  drop-list (`js/language-selector.js`), else the machine's locale (`GET /api/capabilities`
  reports the JVM's language), English as fallback;
  each file names its language under `language.name`, which is what the drop-list shows.
  Switching re-binds the static labels (`data-i18n*` attributes, `translate()`) and redraws
  the page. To add a language: copy `en.json` to `<code>.json`, translate, add the code to
  `LANGUAGES` in `i18n.js`, and (server) add `messages_<code>.properties`. `TranslationsTest`
  checks that every language file has the same keys, that every key the code uses exists,
  and that no key is left unused.

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

An *ego graph* of the selected node: the node in the centre, every link it makes as a row on
the right, every link made to it as a row on the left — one arrow per link, so a type used
twice (`shipTo` and `billTo`) is drawn twice. The arrows carry no text (an optional link — `min` 0 — is dashed): the name of the link,
followed by its cardinality when it has one, is written as a caption above the node it leads
to (or comes from), and repeated in the node's tooltip: element and attribute names in the page's text style, the XSD words (`type`,
`extends`, `list of`…, `STRUCTURAL_LINK_LABELS`) small and muted; the word "attribute" of
the model's labels is not drawn (the node's kind says it), so `attribute orderDate` reads
`orderDate` and `attribute ref` reads `ref`. The details panel keeps the full labels. With the **2 levels** toggle (remembered in `localStorage`) two more
columns show, for every level-1 target its own targets, and for every level-1 user its own
users, as trees: a level-1 node spans as many rows as it has children and sits in the middle
of them; a node reached by several parents is drawn once per parent, and an object reached
by several links from the centre (`shipTo` and `billTo` to `USAddress`) is expanded only
under its first copy — the other copies stay leaves, so nothing is drawn twice. The other open tabs
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
| `GET /`, `/style.css`, `/js/*.js`, `/i18n/*.json` | – | the static asset (classpath `web/`, `Cache-Control: no-cache`). Paths are restricted to `(/[A-Za-z0-9._-]+)+` without `..`. |
| `POST /api/parse` | body: the XSD text (UTF-8) | `200` + the JSON model, or `400` + `{"error": "…"}` (not XML, root not `xs:schema`, …). |
| `GET /api/initial` | – | `200` + `{"name", "path", "text"}` of the file given on the command line, `404` otherwise. The page calls it once at load. |
| `POST /api/quit` | – | `200` + `{"ok":true}`, then the server stops and the process exits (File ▸ Quit). |
| `GET /api/capabilities` | – | `{"dialogs": bool, "language": "fr", "version": "1.8.0", "javaVersion": "21.0.12"}`: whether the server can show native file dialogs (not headless) — the page disables the workspace commands and falls back to the browser's file dialog otherwise — the language of the machine's locale (the page's default language), and the versions shown by Help ▸ About (`BuildInfo`: the jar manifest's `Implementation-Version`, "dev" without one). |
| `POST /api/choose` | – | shows the native "open files" dialog; `200` + `{"files": [{"name", "path", "text"}…]}` (empty when cancelled), `409` without a display. |
| `POST /api/choose-folder` | – | shows a folder chooser (Swing `JFileChooser`: the native dialog cannot pick folders); `200` + `{"folder", "files": [{"name", "path", "text"}…], "truncated"}` — the `.xsd` files of the folder and its sub-folders (depth ≤ 8, at most 200, hidden directories skipped, sorted), or `{"cancelled": true}`; `409` without a display. |
| `POST /api/workspace/save` | body: `{"files": [paths…], "active": n, "path": …}` (`path`, optional: the workspace file to propose) | shows the native "save as" dialog, writes the workspace there (`.xsdviewer.json` appended if missing); `200` + `{"path"}` or `{"cancelled": true}`, `400` for a bad body, `409` without a display. |
| `POST /api/workspace/open` | – | shows the native "open" dialog; `200` + `{"workspace", "active", "files": [{"name", "path", "text"}…], "missing": [paths…]}`, or `{"cancelled": true}`; `400` when the file is not a workspace, `409` without a display. `GET /api/initial` answers the same shape when the command-line file is a workspace. |
| `GET /api/open?base=…&location=…[&strict=true]` | query: `base` = server path of the referencing file (may be empty), `location` = its `schemaLocation` | `200` + `{"name", "path", "text"}` of `location` resolved against `base`'s directory (if `base` is a file the server already served), else — unless `strict` — against the directories of all served files, else against the working directory; `400` for a remote location (`://`), `404` if not found. |
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
                 "label": "shipTo", "min": 1, "max": 1 } ]
}
```

`min` / `max` are present on the links that have a cardinality — nested elements and `group ref`
(their `minOccurs`/`maxOccurs`, multiplied by those of the enclosing `sequence`/`all`/`choice`
since the nearest enclosing element; a `choice` sets `min` to 0), `element ref`, and attributes
(`use`: required → 1..1, optional → 0..1, prohibited → 0..0); `max` is -1 for `unbounded`. Type
links (`type`, `extends`, `restricts`, `list of`, `union of`, `substitutes`, `attributeGroup`)
have none. The client draws links with `min` 0 as optional (dashed).

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
| `java.awt.Desktop`, `java.awt.FileDialog` | `java.desktop` | opening the browser at start-up; the native file dialogs (both guarded: headless JVMs fall back) |
| virtual threads (`Executors.newVirtualThreadPerTaskExecutor`) | `java.base` | one thread per request |

Browser side: plain ES2020 JavaScript, DOM, SVG, `fetch`, the File and Drag-and-Drop APIs.
No external script or stylesheet is loaded.

Build and test:

| Tool | Version | Role |
|---|---|---|
| Maven | 3.9 | build; `mvn package` produces `target/xsdviewer.jar` |
| maven-compiler-plugin | 3.13.0 | `--release 21` |
| maven-jar-plugin | 3.4.1 | sets `Main-Class: org.jtools.xsdviewer.XsdViewerApplication` (no shading needed: no dependencies) |
| maven-surefire-plugin | 3.2.5 | runs the tests |
| JUnit Jupiter | 5.8.2 (test scope) | `XsdParserTest` and `SchemaGraphJsonWriterTest` (against `samples/purchaseOrder.xsd`), `JsonWriterTest`, `CommandLineOptionsTest`, `TranslationsTest` |
| `run.sh` / `run.bat` | – | rebuilds the jar when sources are newer, then runs it (Linux/macOS, Windows) |
| `src/dist/xsdviewer.sh` / `xsdviewer.bat` | – | launchers of the distributions; on Windows the `.bat` starts `javaw.exe` from a command line (`--console` to keep one) |
| launch4j-maven-plugin | 2.7.0 | `dist` profile only: builds `XsdViewer.exe`, a GUI-subsystem Windows launcher (no console window) running the bundled `jre\` with `xsdviewer.jar`; arguments are passed through |
| `build.sh` / `build.bat` | – | `mvn package` |
| `package.sh` / `package.bat` | – | `mvn package -Pdist`, after checking the JRE archives are present |
| maven-clean-plugin | 3.2.0 | `dist` profile only: deletes the previous `xsdviewer-*-windows.zip` / `-linux.tar.gz` and `target/jre` before the build |
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
    ├── main/java/org/jtools/xsdviewer/   XsdViewerApplication, CommandLineOptions, BrowserLauncher, Messages, MessageKey
    │   ├── schema/                      XsdParser, DeclarationLineIndex, SecureXmlFactories, SchemaGraph,
    │   │                                SchemaGraphJsonWriter, NodeKind, LinkLabel, XsdVocabulary
    │   ├── server/                      XsdViewerServer, ApiPath, *Handler, ServedSchemaFiles, SchemaFileFinder, FileDialogs, ...
    │   ├── workspace/                   Workspace
    │   └── json/                        JsonWriter, JsonReader, JsonStrings, JsonKey
    ├── main/resources/org/jtools/xsdviewer/  messages.properties, messages_fr.properties
    ├── main/resources/web/              index.html, style.css, js/*.js, i18n/en.json, i18n/fr.json
    ├── main/resources/embedded/jre/     JRE archives bundled by the dist profile (git-ignored)
    └── test/java/org/jtools/xsdviewer/   CommandLineOptionsTest, TranslationsTest, schema/, json/, workspace/
```

## Extension points

- **Merging a schema set.** `XsdParser` works on one text; the multi-file view is built by
  the client from one tab per file (links followed on demand, linked schemas opened
  automatically when the file's location is known, workspaces to reopen a set). A merged
  single graph would need the parser to take several texts keyed by target namespace.
- **More link kinds** are a new `case` in `XsdParser.collect()` plus a `LinkLabel`; the
  client needs nothing (labels are free text).
- **Other graph layouts** only touch `js/graph.js`; the rest of the client depends on
  `select()` and the tab state, not on how the SVG is built.
- **Node details** (e.g. facets, cardinalities) would extend `SchemaGraph.Node`,
  `SchemaGraphJsonWriter` and `JsonKey`, and `js/details.js` on the client.
- **Another language** is one more `i18n/<code>.json` (+ the code in `LANGUAGES` of
  `js/i18n.js`) and one more `messages_<code>.properties`; `TranslationsTest` flags any
  missing key.
