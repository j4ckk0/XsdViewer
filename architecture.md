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

### Server – `core/` and `app/src/main/java/org/jtools/xsdviewer/`

The Java side is two Maven modules: **core** (`org.jtools:xsdviewer-core`), the parsers, the validators, the JSON writer and the messages — a library with no dependency but the JDK — and **app**, the server and the page, whose jar embeds core's classes so that `java -jar xsdviewer.jar` needs nothing else. The tests run with the root of the repository as working directory in both modules (the parent pom's surefire configuration), so they read `samples/` and `app/src/main/resources/web` from there.

Each class does one thing and is named for it; the packages follow the tiers of the diagram.

| Class | Role |
|---|---|
| `XsdViewerApplication` | Entry point: reads the `CommandLineOptions`, checks the initial file, starts the `XsdViewerServer` (with the automatic stop unless `--keep-alive` / `--no-browser`), prints the URL and opens the browser. |
| `CommandLineOptions` | Record of the options (`--port`, `--host`, `--no-browser`, `--keep-alive`, `-h`, optional `file.xsd`) and their parser; `stopWhenNoPage()` = neither `--keep-alive` nor `--no-browser`. |
| `BrowserLauncher` | Opens a URL with `java.awt.Desktop`, falling back to `xdg-open`. |
| `Log` | The log (`java.util.logging`): console and `xsdviewer.%g.log` in the temporary directory; `INFO` and up — what happens to the server and what fails — or, with `--verbose` (`Log.setVerbose`), `FINE` too: each request received and each schema parsed; every handler failure is logged with its stack trace by `XsdViewerServer` and answered as a 500 with a message. |
| `UserSettings` | The settings changed from the page's Settings menu, kept in the user's `java.util.prefs.Preferences` (`org/jtools/xsdviewer`): `autoStop`. The command line wins for a run. |
| `Messages`, `MessageKey` | The texts the server prints or sends to the page (console, API errors, generated documentation of placeholder nodes), read from `messages.properties` (English) / `messages_fr.properties` for the JVM locale; `MessageKey` holds the keys. |
| `server.XmlValidator`, `server.ValidateHandler` | `POST /api/validate?schema=<xsd>&schematron=<sch>&phase=`: the JDK validator compiles the XSD from its file (a file the server served, never an arbitrary path; imports resolve next to it, files only) and validates the posted document; the Schematron goes through `SchematronValidator`; the problems of both are answered with their line and column (up to 200 each), tagged with their `source`. |
| `schema.SchematronValidator` | The Schematron evaluator over the JDK's XPath 1.0 engine (`javax.xml.xpath`): the `ns` prefixes, the phase (the one asked, else `defaultPhase`, else every pattern), abstract patterns instantiated by textual substitution of their `$param`s in every expression, abstract rules whose assertions and `let`s are collected by `extends` (recursively, each once), `let`s at the schema / pattern / rule levels (a node-set stays a `NodeList`). What an expression is evaluated with — the variables in force, the parameters of the abstract pattern being instantiated — travels down the calls as an immutable `Frame`; the variable resolver reads the frame of the expression being evaluated. A rule's context (an XSLT pattern) becomes a selection from the root: `//` before each top-level alternative not starting at `/`; a node fires the first rule of a pattern that selects it. An expression that does not compile is reported once as `unsupported`. Each problem names the assertion, rule and pattern that fired with the node ids of `SchematronParser` (`elementIds()`), which is what lets the page select them (`SchematronValidatorTest.problemsNameNodesOfTheGraph` pins that contract). `Severity` is the vocabulary: `error`, `warning`, `info` (from the `role` / `flag`), `unsupported`. |
| `schema.SchematronDom`, `SchematronMessage`, `SchematronIncludes`, `LocatedDocument` | What the parser and the validator share. `SchematronDom`: walking a Schematron's own elements (children, descendants, by id; a foreign element is skipped). `SchematronMessage`: the text of an assertion or diagnostic, with a `Leaf` saying what a `value-of` and a `name` become — placeholders (`{select}`, `{name()}`) for the graph, the value on the node for the validator, which then appends the diagnostics the assertion names. `SchematronIncludes`: each `include` replaced in place by the root of the file next to the schema (files only, depth ≤ 16, nested ones relative to the included file), the unreadable ones reported. `LocatedDocument`: the document parsed SAX → DOM with the start-tag position kept as user data on each element (namespace declarations kept as attributes; comments and processing instructions dropped), so a problem carries the line and column of its context node and its path (`/po:purchaseOrder/po:items/po:item[2]/po:quantity`). |
| `schema.SchemaParser` | Entry point for a file's text: an `xs:schema` root goes to `XsdParser`, a `wsdl:definitions` root to `WsdlParser`, a root in a Schematron namespace (`sch:schema`, or a fragment: a `pattern`, a `rule`…) to `SchematronParser`, anything else is refused. |
| `schema.XsdParser` | The only class that knows XSD. Turns an `xs:schema` element into `SchemaGraph` nodes and edges in three passes (see below) with the JDK DOM parser; the passes take any `xs:schema` element so that the schemas inline in a WSDL are parsed into the WSDL's graph. It walks the vocabulary and hands every name it meets to `References`. |
| `schema.References` | What a name stands for, and what becomes of what it names: one rule turning a qualified name into a node id (`type()`: a built-in, a declaration of this file, else a reference), the links whose target is not known yet, and the third pass closing them — a placeholder node for whatever the file references without declaring, and the content models resolved the same way. It answers `ContentModelBuilder.Ids`, so the content models name what the links name; the schemas inline in a WSDL share one, which is why the third pass runs once for the file. |
| `schema.WsdlParser` | The only class that knows WSDL 1.1 (`WsdlVocabulary`): services, portTypes, operations (named within their portType: `operation:P.op`), bindings and messages become nodes; the inline schemas go through `XsdParser`; the links follow service → portType (labelled with the port) → operation → message (`input` / `output` / `fault`) → element or type (labelled with the part), plus binding → portType (`binds`). References are resolved by `XsdParser`'s third pass, so an element of an imported schema is an `external` placeholder in its namespace, resolved by the page in the other tabs like any schema reference. |
| `schema.SchematronParser` | The only class that knows Schematron (`SchematronVocabulary`: the ISO namespace and the 1.5 one). Phases, patterns, rules, assertions (`assert` / `report`) and diagnostics become nodes; the links follow phase → pattern (`active`) → rule (`rule`) → assertion (`assert` / `report`) → diagnostic (`diagnostic`), plus pattern → abstract pattern (`is a`) and rule → abstract rule (`extends`). Nothing there is a QName: a reference is an id, resolved in the file or made an `external` placeholder with an empty namespace. Naming: an `id` when there is one, else the expression (a rule's context, an assertion's test — kept whole in the node's `xpath`), a pattern's `name` / `title` / rank; an unnamed rule or assertion is scoped by its parent (`rule:pattern/context`, `assert:pattern/context/test`) and a duplicate gets `#2`. `include`s are `Import`s. |
| `schema.DeclarationLineIndex` | SAX pass locating the start tag of each declaration (line numbers); the parser says which tag paths declare a node (`DeclarationId`): for a WSDL, the operations at depth 3 and the inline schemas' declarations at depth 4 too. `elementLines()` is the other way in: the line of every element in document order, for a vocabulary whose declarations carry no name — `SchematronParser` numbers the DOM elements in the same order. |
| `schema.SecureXmlFactories` | DOM / SAX factories with external entities and DTD loading disabled. |
| `schema.SchemaGraph` | Plain data: `Node`, `Edge`, `Import` records, the `targetNamespace`, `nodeId(kind, name)`. `nodes` is a `LinkedHashMap` (declaration order is kept), `edges` a `LinkedHashSet` (parallel identical edges collapse). |
| `schema.NodeKind`, `schema.LinkLabel`, `schema.XsdVocabulary`, `WsdlVocabulary`, `SchematronVocabulary` | The constants of the model: the kinds of node, the edge labels, and the namespace / element / attribute names each parser reads. |
| `schema.SchemaGraphJsonWriter` | `SchemaGraph` → JSON (keys in `json.JsonKey`). |
| `schema.ContentModelBuilder`, `schema.ParticleKind` | The content model of a global declaration for the Model view: the tree of its particles and its attributes, walked from the DOM by `XsdParser`'s second pass with the parser's own resolution of names to node ids (`typeId()`), so that the tree names what the links name. A particle and an attribute are built by named factories (`Particle.compositor / element / reference / baseType / wildcard`), the records having more fields than a call site can read positionally. |
| `json.JsonWriter`, `json.JsonStrings`, `json.JsonKey` | A minimal streaming JSON writer and the string escaping. The model is flat enough that a JSON library would be the only dependency of the project, so it was left out. `JsonKey` is the API contract, mirrored by the client. |
| `server.XsdViewerServer` | Starts a `com.sun.net.httpserver.HttpServer` bound to `127.0.0.1:8080` by default and maps each path of `ApiPath` to its handler. Requests are handled on virtual threads (`Executors.newVirtualThreadPerTaskExecutor`). |
| `server.ParseSchemaHandler`, `InitialFileHandler`, `OpenSchemaLocationHandler`, `LocateSchemaFileHandler`, `QuitHandler`, `StaticResourceHandler` | One handler per path of the HTTP interface below. |
| `server.SettingsHandler` | `GET` / `POST /api/settings`: `{"autoStop": bool}`, applied to `PageWatch.setEnabled` at once and kept through `UserSettings`. |
| `server.PageWatch`, `AliveHandler`, `ByeHandler` | The pages open on the server and the automatic stop. `/api/alive` is an event stream the page holds for its whole life (a `: ping` comment every 5 s; the write fails when the browser has dropped the page — no timer on the page, so background-tab throttling cannot fool it); `/api/bye` is the beacon a closing page sends. `PageWatch` counts them and, once a first page has been seen, stops the process (`stopAndExit`, like Quit) when none has been open for 15 s — a reload or a wake-up reconnects well within. Disabled by `--keep-alive` / `--no-browser` for a run, or from the Settings menu (kept). |
| `server.ServedSchemaFiles` | The files the server has read and handed to the page: the only directories `/api/open` and `/api/locate` look into. |
| `server.FileDialogs` | The open / save / folder dialogs, one at a time: on Windows and macOS the native `java.awt.FileDialog` (on a platform thread; a Swing chooser for folders, which it cannot pick); elsewhere the desktop's own dialog through `kdialog` (KDE, LXQt) or `zenity` (GNOME and others) when the PATH holds one — exit 0 answers the paths, 1 is cancel, anything else falls back —, else a Swing chooser in the system look and feel. `available()` is false when the JVM is headless. |
| `server.ChooseFilesHandler`, `SaveWorkspaceHandler`, `OpenWorkspaceHandler`, `CapabilitiesHandler`, `WorkspaceResponse` | The dialog-backed endpoints and the answer describing an opened workspace (its files read, the missing ones listed). |
| `workspace.Workspace` | The workspace record (file paths + active tab) and its `*.xsdviewer.json` form: paths relative to the workspace file when they share its root. |
| `json.JsonReader` | Minimal parser (objects, arrays, strings, numbers, booleans, null) for workspace files and the page's requests. |
| `server.SchemaFileFinder` | Bounded walk looking for a file with a given name and content (`/api/locate`). |
| `server.HttpResponses`, `QueryString`, `ContentType`, `HttpMethod`, `HttpStatus` | Reading the request, writing text / JSON / error answers, query parameters, MIME types by extension. |

#### Parsing passes (`XsdParser`, on the file's `xs:schema` or on each one inline in a WSDL)

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
   all other targets are kept as `type:X` / `element:X` / … for the next pass. `typeId()` is the
   one rule for what a type name stands for: the links and the content models both ask it.
   The content model of each declaration is built in this pass too (`ContentModelBuilder`).
3. **Resolution.** `type:X` becomes `complexType:X` or `simpleType:X` when declared; any
   target still unknown gets an `external` placeholder node so the graph never has dangling
   edges. The content models are resolved the same way, since a type declared by another
   schema of the same file (the schemas inline in a WSDL) is only known once every schema has
   been read; `XsdParserTest.contentModelsNameWhatTheLinksName` pins that the two walks over
   the XSD name the same nodes.

Line numbers come from a separate SAX pass: the SAX `Locator` gives the position of the *end*
of a start tag, so the parser walks back in the text to the `<` to report the line where the
declaration opens, even when its attributes span several lines.

Both XML parsers are configured with secure processing and external entities / DTD loading
disabled: the input is an arbitrary user file.

### Web client – `app/src/main/resources/web/`

Static files, no build step, no framework: `index.html`, `style.css`, ES modules under `js/`
(loaded by `<script type="module" src="js/app.js">`) and the texts under `i18n/`.

| File | Role |
|---|---|
| `index.html` | Page skeleton: top bar (the menus, the file name, and `#topbarRight` — the Model / Text / Graph tabs, the exports and the language, one group pushed to the right edge so it holds it whichever of them is hidden), workspace bar (the workspace chips, the comparison's chip, **⇄ Compare**), document tab bar, sidebar (search, Files panel, object list), main area with the three views of a file (model / text / graph, plus the empty state), the comparison place (`#comparison`: its two section tabs, the Objects section `#objectCompare` and the Files section `#compare`), the validation tab, details panel (schema header, the selected object, the Compare group), drop overlay, toast. Every label carries a `data-i18n` / `data-i18n-title` / `data-i18n-placeholder` key. |
| `style.css` | Layout (flexbox: sidebar – main – details), the colour per kind of object (one CSS variable each, reused by sidebar dots, legend, badges and SVG strokes), text-view syntax colours. |
| `i18n/en.json`, `i18n/fr.json` | The texts of the page, one flat JSON file per language (same keys in each, checked by `TranslationsTest`). |
| `js/app.js` | Start-up: `startPresence()`, `loadCapabilities()` + `loadSettings()`, `await initI18n()`, `wireEvents()`, `renderPage()`, `loadInitialFile()`. |
| `js/validate.js` | File ▸ Validate an XML file…, and the validation tab (`tab.validation`: the document, the XSD and / or Schematron used — the shown schema and the first located file of the other kind in the workspace —, the phase, the result). `validateText()` opens (or reuses) the tab and runs `POST /api/validate`; `renderValidation()` draws the verdict and the document's path, one list per kind of schema (the workspace's located files of that kind; `setSchema()` re-runs on a change), the phase list, the problem rows (a Schematron row links to the assertion, rule and pattern, node ids the wiring jumps to in the Schematron's tab) and the document through `highlightXml()` with the problem lines marked; `selectProblem()` / `stepProblem()` keep the row and the line together. A dropped `.xml` file on a schema that can be validated goes here rather than being opened (`file-actions.js`). The module tells the page to redraw through `onChange()`, set by the wiring, since `page.js` depends on it. |
| `js/switch-menu.js`, `js/graph-filters.js` | The two switch menus of the graph toolbar, built on one factory (`switchMenu()`: the values off kept in `localStorage`, the check marks, the button marked while something is off, the entries offered being those of the family it is drawn for — the menu wires its own clicks and calls back what redraws). `graph-filters.js` holds both, and answers `isKindShown()` / `isLinkShown()`. *Links*: the categories of link (`LINK_CATEGORY`, `linkCategory()`). *Types*: the kinds of object, the built-in types among them. `renderGraph()` drops the rows of a hidden category or kind, at both levels and on both sides; the centre is always drawn. |
| `js/link-categories.js` | What a link is, every such question in one place: the family of a kind and of a link (`familyOf()`, `linkFamily()` — a link belongs to a WSDL's or a Schematron's chain as soon as one of its ends does; null is the schema's own structure), the words a family's chain uses (`labelFamily()`), the XSD words (`STRUCTURAL_LINK_LABELS`), the derivations (`isDerivation()`), and the category the *Links* menu switches (`linkCategory()`: content, attributes, types, base types, references, chain). |
| `js/zoom.js` | The zoom of the drawn views (graph, model, comparison), which are SVGs: the level (`session.active.zoom`, one per tab, `ZOOM.STEPS`) multiplies each SVG's `width` / `height` and leaves its `viewBox` alone, so the picture grows and its panel scrolls it — the browser's own zoom would shrink the panels with it. Applied after every drawing, since drawing writes a new SVG. |
| `js/foldable.js` | One helper for everything that folds and remembers it (`foldable({element, toggle, storageKey, titles, glyphs, defaultFolded, onChange})` → `set` / `isFolded` / `toggle` / `refresh` / `init`): the details panel, the schema header, the Files panel and the Compare group are four declarations of it. |
| `js/panels.js` | The widths of the side panels: one splitter (`role="separator"`, focusable) between the sidebar and the main area, another between the main area and the details panel. A pointer drag — or the arrow keys — sets the panel's width, clamped between `PANEL.MIN_WIDTH` and a share of the window, and kept in `localStorage`; a double-click drops the stored width, back to the stylesheet's. The views are laid out for the room they have, so `initPanels(redraw)` is given what redraws the shown one (`renderMainView`), which it calls once a width has changed; the module itself knows nothing of the views. `updateSplitters()` (called by `renderPage()` and by the details panel's collapse) hides the splitter of a panel that is not shown. |
| `js/settings.js` | The Settings menu: `loadSettings()` at start-up, the check mark of *Stop the server when the last page is closed*, `toggleAutoStop()` posting the change and confirming it with a toast. |
| `js/theme.js`, `js/theme-boot.js` | *Settings ▸ Dark theme* / *Light theme*: one entry flipping the theme (the system's setting until chosen), kept in `localStorage`, stamped as `data-theme` on `<html>` — the stylesheet holds one palette of variables and a dark override of it. `theme-boot.js` is a classic script run from `<head>` so that a dark page does not flash white before the modules load. |
| `js/presence.js` | One `EventSource` on `/api/alive?id=` for the page's life (`startPresence()`); `pagehide` closes it and beacons `/api/bye`, `pageshow` from the back/forward cache reopens it; Quit closes it without a beacon. |
| `js/constants.js`, `js/dom.js`, `js/message-keys.js` | The strings of the client: the API contract and vocabulary shared with the server (kinds, paths, storage keys); element ids, CSS classes and data attributes; the keys of the texts. The vocabulary only — what is made of it (what a link is) belongs to `js/link-categories.js`. |
| `js/i18n.js`, `js/language-selector.js`, `js/kind-labels.js` | Language choice (`?lang=`, remembered choice, else the machine's locale, else English), loading of `i18n/<lang>.json`, `t(key, …args)`, `plural()`, `translate(root)` for the `data-i18n*` bindings, `setLanguage()`; the drop-list; labels of node kinds. |
| `js/state.js` | `newTabState()` (one object per document tab: the model plus derived indexes `outEdges`, `inEdges`, `lineToNode`, the selection, history, view, filter and scroll positions, its `workspace` and the workspace `file` it shows), `newWorkspaceState()` (a workspace: `path` once saved / opened, `number` until then, and its `files`) and `session` (the workspaces, their tabs — one flat list grouped in workspace order —, the active tab, the pending jump, the folder library). |
| `js/workspace-files.js`, `js/file-tabs.js` | The files a workspace knows (`{name, path, rel, text, model}`, registered by identity — path on disk or path in an opened folder), open in a tab or only listed; `ensureTab(entry)` opens a listed file in a tab from its cached text and model, `parseInBackground(ws)` parses listed files one by one so that their objects can be browsed. Sets larger than `MAX_AUTO_OPEN` (10) — a folder, a workspace, linked schemas found at once — are listed, not opened. |
| `js/api.js` | The `fetch` calls to `/api/*`, one function per path. |
| `js/schema-index.js`, `js/declarations.js` | Indexing a parsed schema into a tab; finding declarations across the tabs of a workspace (`findIn`, `findInTabs`, `usersInOtherTabs`, `locationsFor`) — never across workspaces. |
| `js/folder-library.js`, `js/schema-loader.js` | The folder library (Open folder… / dropped folders); `loadInto(tab, …)` (parse through the server, index, locate) and `resolveLocation()` (library, then server; `strict` = relative to the file only). |
| `js/linked-schemas.js` | `openLinkedSchemas(tab)`: finds the schemas linked (strictly relative to the file) from a file whose location is known, recursively, registers them in the workspace and opens them in tabs when there are at most 10; serialised and capped. |
| `js/tabs.js`, `js/page.js` | Workspaces and their tabs (`newWorkspace`, `newTab(ws)`, `tabsOf(ws)`, activate / close a tab or a workspace, `closeAllTabs`; the names of the comparison and validation tabs) and `renderNavigation()`: the workspace bar, the tab bar (the active workspace's tabs) and the Files panel — what background loads redraw; `renderPage()` redraws everything from the active tab, `showView(view)` switches model / text / graph and draws the view it shows — of the active tab's file, or of the comparison — naming first the place being read (`placeShown()`: a file, nothing, a validation, one of the comparison's sections) and reading from the `SHOWS` table what that place shows of the page, which is a place rather than a view: `session.comparison` says whether its chip is on the workspace bar (`open`), whether it is the place being read (`shown`, the active tab staying where the reader left it), which of its two sections it draws (`section`), and how its Objects section draws the two declarations (`view`, the comparison's own: switching there leaves every tab's view alone; `currentView()` says whose view is being read). The Files section hides the view tabs, being a list of file pairs. `renderMainView()` is the one place that draws the graph or the model, whichever the active tab shows: everything that changes what they draw, or the room they have — a selection, a tab switch, a panel dragged, the window resized, another file of the workspace loaded — calls it, and neither view is drawn while hidden. |
| `js/busy.js` | The busy indicator of the top bar (a wheel and a label, shown after 200 ms so that quick actions do not flash it): `busy(label, action)` around opening files, reading a folder, opening a workspace, looking for linked schemas; `beginBusy` for the background parsing, whose label counts the files left. |
| `js/navigation.js` | `select(id)` drives every view and keeps the back history (the object list opens the group of the selection when it was folded, so that a click in the graph shows where the object is); `followExternal()` follows a link into another file (see below). |
| `js/about.js` | Help ▸ About: a `<dialog>` with the version and Java runtime reported by the server, the licence and the project page. |
| `js/comparison.js`, `js/compare-selection.js` | The comparison as a place: `comparison.js` owns `session.comparison` (open / shown / section / view), the two sides (`session.compared`, `markSide`, `swapSides`, `clearMarks`, `comparedPair()`), where a side is read from (`placeOf(mark)`: its tab while open and parsed, else its file in the workspace) and the opening and closing of the place; `compare-selection.js` is the one writer of `session.compareSelection` (`toggleSelection`, `clearSelection`, `pruneSelection`, `canCompare`), the others read it. |
| `js/compare.js` | The **Files** section of the comparison: the selection (Ctrl+click on chips, `session.compareSelection`, two at most; **⇄ Compare** opens the comparison on this section when two are selected, on the Objects section otherwise — `canCompare()`), the files of the two workspaces paired by name, their statuses, a differing pair expandable in place to its schema and line differences (`textDiffHtml(diff, fold)`, which the Objects section reuses for two declarations), the pairs and their statuses asked of `POST /api/compare/workspaces`, a differing pair's schema and line differences of `POST /api/compare/schemas` and `POST /api/compare/texts` when its row is opened; `textDiffHtml(diff, folding)` draws the lines side by side, moved blocks in their own colour, long identical runs folded. |
| `js/object-compare.js`, `js/declaration-source.js`, `js/model-requests.js` | The **Objects** section of the comparison: two declarations side by side, wherever each of them lives, drawn the way the comparison's own view asks — **Model**, **Text** or **Graph**, one entry of `VIEWS` each: what it draws into the two panes and the summary it writes. The comparing is the server's: `object-compare.js` asks `POST /api/compare/declarations` once per pair (`model-requests.js` says what a side carries: the parsed files of its workspace, its file among them, its declaration) and keeps the answer — the two trees marked, the counts, the links only one side has — for the folds and the graph view; the text view cuts each declaration's lines out of its file (`declaration-source.js`) and has `POST /api/compare/texts` align them on their shape. `comparison.js` owns the two sides (`session.compared.left` / `.right`, each holding one declaration or nothing; a side keeps the tab, the workspace and the file entry, so it survives its tab closing, and is emptied when its workspace goes). A side is chosen from the details panel, never assigned by the order things were picked in, and `comparedPair()` gives the two only when both are filled: nothing is drawn while one is empty. `model-diff.js` marks every box of the two content models (built by `buildTree(…, {openAll: true})`, so the whole shape is compared, not what the reader unfolded) as same, changed, removed or added — the boxes matched by what each one is, in order, by the same LCS as the lines, so an element inserted on one side does not shift what follows. Each side is drawn by `modelSvg(…, {foldable: true})` of the Model view, where every box holding something carries a handle: the boxes put aside are kept in `session.comparedFolded`, by the trail `model-diff.js` gives each box — what it and its parents are, from the root — so folding a box folds the one matching it on the other side, the folds outlive a redrawing, and the models are compared whole whatever is folded. A workspace's file is read through `placeOfEntry()`, so neither file need be open in a tab. In **Text**, each side is the source of its declaration alone — the lines from `node.line` to `node.endLine`, numbered as in the file — the two aligned by `diffLines()` on their lines with spacing collapsed, so the same declaration written at another depth still matches, and drawn by `textDiffHtml()` unfolded, in one scrolling area under the two names so the rows stay side by side. In **Graph**, each side is its neighbourhood drawn by `renderGraph(place, canvas, {toolbar: false, markOf})`, a link keyed by its label, its target's kind and name and its cardinality, the keys the other side lacks marked `del` / `ins`. The header's summary counts what the drawn view compares: boxes, lines or links; the fold tools show for the models only. |
| `js/file-actions.js`, `js/workspace-actions.js`, `js/capabilities.js`, `js/events.js` | The File menu on files (open through the server's dialog or the browser's, close, quit, the start-up file); on workspaces (new / open / save / close, a folder opened as a workspace named after it — an opened workspace is its own group of tabs, saving writes the active workspace); what the server can do (dialogs, language, versions) and the menu entries depending on it; wiring of the controls, keys and drops to the actions — for what a module owns alone (the panels, the graph's menus, the Model view) that module wires itself, `app.js` giving it what it must call back. |
| `model.ContentTree`, `model.Box`, `model.Library` (core) | The content model of a declaration as a tree of boxes, which the Model view draws and the comparison of two declarations walks. `ContentTree.build(root, home, library, expanded, openAll)` turns the particles into boxes (a compositor, an element, a group reference, a wildcard, a base type, an attribute) walking anonymous types in place and opening a named type, a global element, a group or a base type on demand — the box's path in the tree is what the page keeps in `tab.modelExpanded` and sends — from that node's own content (this file, else another file of the `Library`, which says which file declares a name), a node already open above being drawn as recursive; A declaration of a WSDL or of a Schematron has no content model, but `openingOf()` then gives its chain (`chainLinksOf()`: the links out of a node whose kind belongs to a family), one box per link, named after what it leads to with the link's word above it — the two open the same way, and where a chain reaches a schema element the content model takes over. Each box remembers the file it was read from, so a walk that leaves the shown file (a WSDL's message to an element of an imported schema) follows that file's own links. nothing there draws, and the tree is a function of its arguments alone, which is what `ContentTreeTest` exercises; `compare.ModelDiff` marks two such trees (`ModelDiffTest`), `compare.LineDiff`, `compare.BusinessLines` and `compare.SchemaDiff` do the line, business-line and schema comparisons (their tests likewise). |
| `js/model-view.js` | The Model view: `layout()` gives each leaf a row and centres a parent on its children, `modelSvg()` draws the elbow connectors and the boxes (`boxSvg()`) — the comparison of two declarations draws each side with it, and the exports take the SVG as they take the graph's. The module wires its own buttons and canvas (`initModelView(select)`, called from `app.js` with what selects a declaration). |
| `js/file-list.js`, `js/sidebar.js`, `js/graph.js`, `js/details.js`, `js/text-view.js`, `js/xml-highlighter.js`, `js/png-export.js` | One module per view: the Files panel (the active workspace's files as a tree by folder, each unfoldable to its objects, narrowed to the matching objects of the matching files while the search box holds a text, redrawn with the tab bar; the head counts the files that answer, and two last rows count what the search cannot see — the files still being parsed and those that could not be parsed at all), object list and schema header (foldable, at the top of the details panel; `renderNodeListSelection()` and `renderFileListSelection()` move the highlight to the object being read in each panel, without rebuilding either), SVG ego-graph (its legend shows the WSDL / Schematron kinds for such a file, and no XSD kind for a Schematron), details panel (collapsible to a strip; a node's `xpath` — a Schematron rule's context, an assertion's test — in a code box above the documentation), source text and its tokenizer. Folded states are remembered in `localStorage`. |
| `js/png-export.js`, `js/text-export.js`, `js/file-download.js` | The exports. `png-export.js` serves the views that are drawings: the SVG cropped to what it holds, with the page's CSS and background put under it so the file renders alone (`standalone()`), the comparison exported as one picture holding its two drawings — the content models or the neighbourhoods — under the heading each pane carries (its text view draws nothing to export), and the PNG being that SVG rasterised. `text-export.js` paints the source line by line onto a canvas, the Text view being HTML rather than a drawing. `file-download.js` hands the file to the browser. |

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

A file's own objects — a WSDL's service, portType, operation, binding and message, a Schematron's
phase, pattern, rule, assertion and diagnostic — are drawn with rounded corners, and every link with
one of them at either end (`linkFamily()`) is drawn in the family's colour, heavier, its word in the
same colour: the service or the rules read apart from the structure of the data, which stays grey
and square. The arrowheads are one shared marker per shape, filled with `context-stroke` so that
they take the colour of the line they end.

An *ego graph* of the selected node: the node in the centre, every link it makes as a row on
the right, every link made to it as a row on the left — one arrow per link, so a type used
twice (`shipTo` and `billTo`) is drawn twice. The arrows carry no text (an optional link — `min` 0 — is dashed): the name of the link,
followed by its cardinality when it has one, is written as a caption above the node it leads
to (or comes from), and repeated in the node's tooltip: element and attribute names in the page's text style, the XSD words (`type`,
`extends`, `list of`…, `STRUCTURAL_LINK_LABELS`) small and muted; the word "attribute" of
the model's labels is not drawn (the node's kind says it), so `attribute orderDate` reads
`orderDate` and `attribute ref` reads `ref`. The details panel keeps the full labels. With the **2 levels** toggle (remembered in `localStorage`) one more
column shows, on the right only, for every level-1 target its own targets, as trees: a
level-1 node spans as many rows as it has children and sits in the middle of them, and an
object reached by several links from the centre (`shipTo` and `billTo` to `USAddress`) is
expanded only under its first copy — the other copies stay leaves, so nothing is drawn twice.
The left side (what uses the centre) always stays one step deep: that is what is useful there. The other open tabs
take part: a level-1 `external` target declared in another tab (`findInTabs()`) is drawn
with its real kind and file name and expanded from that tab's model, and nodes of other tabs
that reference the centre or a level-1 user through an external placeholder
(`usersInOtherTabs()`) appear on the left. Nodes of another tab carry `data-tab`, and
clicking one activates that tab before selecting. A self-reference (recursive type) is drawn as a loop above
the centre. The SVG is generated as a string and inserted with `innerHTML`; there is no layout
library because the layout is two columns and a cubic Bézier per edge. `renderGraph(place, canvas, options)`
draws the active tab into the graph view by default; the comparison draws each of its sides with it into
its own pane, without the toolbar (`toolbar: false`) and with a `markOf(node, edge)` hook giving a row the
class marking what the other side lacks. Clicking a node calls
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
| `POST /api/parse` | body: the text of the file (XSD, WSDL or Schematron; UTF-8) | `200` + the JSON model, or `400` + `{"error": "…"}` (not XML, root not `xs:schema` / `wsdl:definitions` / Schematron, …). |
| `GET /api/initial` | – | `200` + `{"name", "path", "text"}` of the file given on the command line, `404` otherwise. The page calls it once at load. |
| `POST /api/quit` | – | `200` + `{"ok":true}`, then the server stops and the process exits (File ▸ Quit). |
| `GET /api/alive?id=…` | query: the page's random id | `200` `text/event-stream`, kept open: `: ping` every 5 s until the page goes away. The page is counted as open meanwhile (`PageWatch`). `400` without an id. |
| `POST /api/bye?id=…` | query: the page's id (sent as a beacon from `pagehide`) | `200` + `{"ok":true}`; the page is no longer counted. |
| `GET /api/settings`, `POST /api/settings` | `POST` body: `{"autoStop": bool}` | `200` + `{"autoStop": bool}`, the current settings; a `POST` applies them first (the automatic stop is armed or disarmed at once) and keeps them for the next runs. `400` for another body. |
| `GET /api/capabilities` | – | `{"dialogs": bool, "language": "fr", "version": "3.6.1", "javaVersion": "21.0.12"}`: whether the server can show native file dialogs (not headless) — the page disables the workspace commands and falls back to the browser's file dialog otherwise — the language of the machine's locale (the page's default language), and the versions shown by Help ▸ About (`BuildInfo`: the jar manifest's `Implementation-Version`, "dev" without one). |
| `POST /api/choose` | – | shows the native "open files" dialog; `200` + `{"files": [{"name", "path", "text"}…]}` (empty when cancelled), `409` without a display. |
| `POST /api/choose-folder` | – | shows a folder chooser (Swing `JFileChooser`: the native dialog cannot pick folders); `200` + `{"folder", "files": [{"name", "path", "text"}…], "truncated"}` — the `.xsd` / `.wsdl` / `.sch` files of the folder and its whole sub-tree (symbolic links followed, depth ≤ 64, at most 2000, hidden and unreadable directories skipped, sorted), or `{"cancelled": true}`; `409` without a display. |
| `POST /api/workspace/save` | body: `{"files": [paths…], "active": n, "path": …}` (`path`, optional: the workspace file to propose) | shows the native "save as" dialog, writes the workspace there (`.xsdviewer.json` appended if missing); `200` + `{"path"}` or `{"cancelled": true}`, `400` for a bad body, `409` without a display. |
| `POST /api/workspace/open` | – | shows the native "open" dialog; `200` + `{"workspace", "active", "files": [{"name", "path", "text"}…], "missing": [paths…]}`, or `{"cancelled": true}`; `400` when the file is not a workspace, `409` without a display. `GET /api/initial` answers the same shape when the command-line file is a workspace. |
| `GET /api/open?base=…&location=…[&strict=true]` | query: `base` = server path of the referencing file (may be empty), `location` = its `schemaLocation` | `200` + `{"name", "path", "text"}` of `location` resolved against `base`'s directory (if `base` is a file the server already served), else — unless `strict` — against the directories of all served files, else against the working directory; `400` for a remote location (`://`), `404` if not found. |
| `POST /api/locate?name=…` | body: the text of a file opened in the browser | `200` + `{"path"}` of a file with that name and content under the served files' directories or the working directory (depth ≤ 8, ≤ 50 000 entries, hidden directories skipped), `404` otherwise. |
| `POST /api/validate?schema=…&schematron=…&phase=…` | query: the server paths of the XSD and / or the Schematron (files the server has served), the Schematron phase (empty: the schema's default; `#ALL`: every pattern); body: the XML document | `200` + `{"valid", "problems": [{"source": "xsd" / "schematron", "severity": "error" / "warning" / "info" / "unsupported", "line", "column", "message", "location"?, "assertion"?, "rule"?, "pattern"?, "test"?}], "truncated", "phases"?, "phase"?, "checked"?}` — the Schematron keys only when one ran; `400` when neither is named or a schema cannot be compiled (the phase unknown, the Schematron not one), `404` for a path the server did not serve. |

| `POST /api/model` | body: `{"files": [{"name", "text"}…], "home": n, "id": "…", "expanded": [paths], "openAll": bool}` — the parsed files of the declaration's workspace, the index of the one it is read from, its node id, the paths of the boxes opened | `200` + the content model tree (`model.Box` as JSON: `kind`, `name`, `path`, `ref`, `typeId`, `typeName`, `word`, `namespace`, `card {min, max}`, `expandable` / `expanded` / `recursive` / `root` when set, `attributes`, `children`), a named type it uses read from the other files; `400` when the home file is not a schema or declares no such id. Stateless: the texts travel with every call, a parsed text being kept a while by its hash. |
| `POST /api/compare/declarations` | body: `{"left": side, "right": side}`, a side being `{"files", "home", "id"}` as above | `200` + `{"left": tree, "right": tree, "counts": {same, changed, removed, added}, "links": {"onlyLeft": [{label, kind, name, min?, max?}…], "onlyRight": […]}}`: the two trees whole, every box marked `diff` and given its `foldKey` (`compare.ModelDiff`); a tree is null when its side declares nothing; the links of each neighbourhood the other side lacks, for the graph view. |
| `POST /api/compare/texts` | body: `{"left": text, "right": text, "businessOnly": bool, "ignoreSpacing": bool}` | `200` + `{"la": [{n, text}…], "lb": […], "ops": [{op, a?, b?, moved?, movedTo?, movedFrom?}…] or null, "onlyMoves": bool}`: the lines compared with their number in the original text, the edit script (`compare.LineDiff`; null when too different to align), whether only blocks moved. |
| `POST /api/compare/schemas` | body: `{"left": text, "right": text}` | `200` + `{"schemas": true, "same", "nodesOnlyLeft": [{id, kind, name}], "nodesOnlyRight", "edgesOnlyLeft": [{from, to, label, min?, max?}], "edgesOnlyRight"}` (`compare.SchemaDiff`), or `{"schemas": false}` when either text is not a schema. |
| `POST /api/compare/workspaces` | body: `{"left": [{"name", "text"}…], "right": […], "businessOnly": bool}` | `200` + `{"pairs": [{"name", "status"}…]}`, the names sorted, a status being `same`, `moved`, `different`, `only-left` or `only-right`. |

The server binds to `127.0.0.1` unless `--host` says otherwise: it is a local tool, not a
service, and it parses whatever is posted to it.

### JSON model

```json
{
  "targetNamespace": "http://example.com/po",
  "imports": [ { "tag": "import", "namespace": "…", "schemaLocation": "…" } ],
  "nodes":   [ { "id": "complexType:USAddress", "kind": "complexType", "name": "USAddress",
                 "ns": "http://example.com/po", "line": 36, "endLine": 44, "doc": "…" },
               { "id": "rule:orders/po:item", "kind": "rule", "name": "po:item", "ns": "",
                 "line": 12, "endLine": 15, "doc": "", "xpath": "po:item" } ],
  "edges":   [ { "from": "complexType:PurchaseOrderType", "to": "complexType:USAddress",
                 "label": "shipTo", "min": 1, "max": 1, "compositor": "sequence" } ]
}
```

`content` and `attributes` are present on the nodes that have a content model — an element's
anonymous type, a complexType, a group, an attributeGroup — for the Model view. `content` is a
tree of particles, each `{"kind": …}`: a compositor (`sequence`, `choice`, `all`; `min` / `max`;
`children`), an `element` (`name`; `ref` when it refers to a global element, else `type` when it
has a named type, else its anonymous type's `children` and `attributes`; `min` / `max`), a `group`
reference (`ref`), an `any` wildcard (`namespace`), or the base type of a derivation (`extends` /
`restricts`; `type`), first. An attribute is `{"name", "ref"?, "type"?, "min", "max"}`, an
attributeGroup reference or an anyAttribute one without a use. `ref` and `type` are node ids, the
same as the links' targets, so the view opens them from the graph's nodes. `ContentModelBuilder`
walks the declaration for them, `ParticleKind` names the kinds.

`compositor` is present on the links of a nested element or a group reference: the `sequence`,
`choice` or `all` it sits in directly (the content of an element's own type sits in that type's
compositors, not in the one holding the element). The graph marks a choice branch and an all member
in the caption, and the details panel writes the word; a list's and a union's links end in a
diamond, filled and hollow, and a declaration with `values` shows their count on its node.

`min` / `max` are present on the links that have a cardinality — nested elements and `group ref`
(their `minOccurs`/`maxOccurs`, multiplied by those of the enclosing `sequence`/`all`/`choice`
since the nearest enclosing element; a `choice` sets `min` to 0), `element ref`, and attributes
(`use`: required → 1..1, optional → 0..1, prohibited → 0..0); `max` is -1 for `unbounded`. Type
links (`type`, `extends`, `restricts`, `list of`, `union of`, `substitutes`, `attributeGroup`)
have none. The client draws links with `min` 0 as optional (dashed).

`kind` is one of `element`, `complexType`, `simpleType`, `group`, `attributeGroup`,
`attribute`, `builtin`, `external`; for a WSDL `service`, `portType`, `operation`, `binding`,
`message`; for a Schematron `phase`, `pattern`, `rule`, `assert`, `report`, `diagnostic`. `ns` is
the target namespace for a declaration, the referenced namespace for an `external` placeholder
(used to find the file declaring it), the XSD namespace for a `builtin`, empty for a Schematron's
nodes. `line` and `endLine` are 1-based — where the declaration's start tag opens and where its end tag closes (the same line when it is self-closed), `DeclarationLineIndex` reading both from the SAX locator — `0` when the node has no declaration in the file. `values` (an
enumeration), `members` (the names inside a declaration) and `xpath` (the expression a
Schematron rule or assertion is made of) are present only when there is something to say.

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
| Maven | 3.9 | build of the two modules; `mvn package` produces `core/target/xsdviewer-core-<version>.jar`, the library, and `app/target/xsdviewer.jar`, the tool |
| maven-compiler-plugin | 3.13.0 | `--release 21` |
| maven-shade-plugin | 3.6.0 | `app` only: merges core's classes and messages into the application jar once it is made (its module descriptor left out), so the one jar runs alone; the jar plugin's manifest is kept |
| maven-source-plugin, maven-javadoc-plugin, maven-gpg-plugin, central-publishing-maven-plugin | 3.3.1, 3.6.3, 3.2.4, 0.7.0 | `publish` profile only: the library's sources and javadoc jars, their signatures, and the upload to Maven Central's portal (PUBLISHING.md §7); `app` skips the publication |
| maven-jar-plugin | 3.4.1 | sets `Main-Class: org.jtools.xsdviewer.XsdViewerApplication` and the implementation entries About shows |
| maven-surefire-plugin | 3.2.5 | runs the tests, from the root of the repository (`maven.multiModuleProjectDirectory`, pinned by the `.mvn/` directory) |
| JUnit Jupiter | 5.8.2 (test scope) | `XsdParserTest`, `WsdlParserTest`, `SchematronParserTest`, `SchematronValidatorTest` and `SchemaGraphJsonWriterTest` (against the samples), `PageContractTest` (the vocabulary shared with `js/constants.js`: node kinds, link labels, API paths), `JavaScriptTestsTest` (runs `src/test/js/*.test.mjs` — the page's pure modules: diff, business lines, cardinalities, schema diff… — under Node when it is installed, skipped otherwise; the CI installs it), and `scripts/screenshots.py` (a visual smoke test with headless Firefox: the samples opened in the built jar, a few facts checked on the page, one screenshot per scene; the scenes carrying a `doc` name are the README's pictures, written as JPEG in `screenshots/` by `--docs`; `--only=a,b` runs named scenes, and a duplicate scene name is refused, a name being a file name and a result key), `JsonWriterTest`, `JsonReaderTest`, `WorkspaceTest`, `CommandLineOptionsTest`, `TranslationsTest`, `SchemaFolderTest`, and `XsdViewerServerTest` (the HTTP interface on an ephemeral port) |
| `scripts/run.sh` / `scripts\run.bat` | – | rebuilds the jar when sources are newer, then runs it (Linux/macOS, Windows) |
| `app/src/dist/xsdviewer.sh` / `xsdviewer.bat` | – | launchers of the distributions; on Windows the `.bat` starts `javaw.exe` from a command line (`--console` to keep one) |
| launch4j-maven-plugin | 2.7.0 | `dist` profile only: builds `XsdViewer.exe`, a GUI-subsystem Windows launcher (no console window) running the bundled `jre\` with `xsdviewer.jar`; arguments are passed through |
| `scripts/build.sh` / `scripts\build.bat` | – | `mvn package` |
| `scripts/package.sh` / `scripts\package.bat` | – | `mvn package -Pdist`, after checking the JRE archives are present |
| maven-clean-plugin | 3.2.0 | `dist` profile only (in `app`): empties `releases/` at the root (previous `xsdviewer-*-windows.zip` / `-linux.tar.gz` / `-macos.tar.gz` / `.jar`) and deletes `app/target/jre` before the build |
| maven-antrun-plugin | 3.1.0 | `dist` profile only: runs `app/src/build/runtimes.xml`, which unpacks the JDK archives of `jre/` (root), links a runtime per platform, and packs the archives into `releases/` with the jar, a launcher, `samples/` and `README.md` from the root |

`mvn package -Pdist` produces `releases/xsdviewer-<version>-windows.zip`,
`-linux.tar.gz` and `.jar` (git-ignored directory). The JRE archives under `jre/` (a build input, kept out of `src/`) are
git-ignored (downloaded by hand, see README); the antrun step fails when one is missing. The Linux descriptor restores the
executable bits on `jre/bin/*`, `lib/jspawnhelper` and `lib/jexec` that Ant's
`untar` drops.

## Repository layout

```
XsdViewer/
├── pom.xml                       the parent: the two modules, the shared metadata and plugin versions
├── .mvn/                         pins the root of the build (maven.multiModuleProjectDirectory) for the tests and the dist profile
├── core/                         org.jtools:xsdviewer-core — the library
│   ├── pom.xml
│   └── src/main/java/org/jtools/xsdviewer/
│       ├── Messages, MessageKey          the texts (src/main/resources/org/jtools/xsdviewer/messages*.properties)
│       ├── schema/                       SchemaParser, XsdParser, WsdlParser, SchematronParser, DeclarationLineIndex,
│       │                                 SchematronValidator (+ SchematronDom, SchematronMessage, SchematronIncludes, LocatedDocument, Severity),
│       │                                 SecureXmlFactories, SchemaGraph, SchemaGraphJsonWriter, NodeKind, LinkLabel, *Vocabulary
│       └── json/                         JsonWriter, JsonReader, JsonStrings, JsonKey
│   └── src/test/java/                    the parser, validator and JSON tests
├── app/                          org.jtools:xsdviewer — the tool (its jar embeds core)
│   ├── pom.xml                   the dist profile lives here
│   ├── src/build/runtimes.xml    Ant: unpacking the JDKs, jlink, the archives
│   ├── src/dist/                 xsdviewer.bat, xsdviewer.sh launchers
│   └── src/main/java/org/jtools/xsdviewer/
│       ├── XsdViewerApplication, CommandLineOptions, BrowserLauncher, Log, UserSettings, BuildInfo
│       ├── server/                       XsdViewerServer, ApiPath, *Handler, XmlValidator, ServedSchemaFiles, SchemaFileFinder, FileDialogs, ...
│       └── workspace/                    Workspace
│   ├── src/main/resources/web/           the page: index.html, style.css, js/, i18n/
│   ├── src/test/java/                    the server, workspace, command line, log and translation tests, the page's contract
│   └── src/test/js/                      the tests of the page's pure modules
├── examples/                     org.jtools:xsdviewer-examples — programs on the library (src/main/java, run by ExamplesTest) and JavaScript programs on the API (api/*.mjs), on samples/
├── .github/workflows/            build.yml (mvn package on JDK 21, the jar kept as artefact), release.yml (a pushed tag → the release)
├── scripts/                      run, build, package, release, screenshots.py, changelog-section.py
├── README.md, architecture.md, CHANGELOG.md, PUBLISHING.md
├── jre/                          JDK archives the dist profile links runtimes from (git-ignored, downloaded by hand or by the workflow)
├── releases/                     output of the dist profile: zip, tar.gz, jar (git-ignored, README only)
├── screenshots/                  the five pictures of the README, written by scripts/screenshots.py --docs
└── samples/                      one sample per thing the tool does (samples/README.md)
```

## Extension points

- **Merging a schema set.** `XsdParser` works on one text; the multi-file view is built by
  the client from one tab per file (links followed on demand, linked schemas opened
  automatically when the file's location is known, workspaces to reopen a set). A merged
  single graph would need the parser to take several texts keyed by target namespace.
- **Packaging**: `src/build/runtimes.xml` (Ant, called by the pom's `dist` profile) unpacks the
  Temurin JDK archives of `jre/`, links a trimmed runtime per platform with `jlink` and packs the
  archives; `.github/workflows/release.yml` runs it on a pushed tag and publishes the release.
- **More link kinds** are a new `case` in `XsdParser.collect()` plus a `LinkLabel`; the
  client needs nothing (labels are free text). **More node kinds** (as the WSDL or Schematron ones) need
  their `NodeKind`, a colour (light and dark), a legend chip and the `kind.` / `group.` texts on the page.
  **Another vocabulary** is a `*Vocabulary` + `*Parser` pair dispatched from `SchemaParser`; the
  `.ext` goes to `ChooseFilesHandler`, `SchemaFolder`, the page's file patterns and the file input.
- **Other graph layouts** only touch `js/graph.js`; the rest of the client depends on
  `select()` and the tab state, not on how the SVG is built.
- **Node details** (e.g. facets, cardinalities) would extend `SchemaGraph.Node`,
  `SchemaGraphJsonWriter` and `JsonKey`, and `js/details.js` on the client.
- **Another language** is one more `i18n/<code>.json` (+ the code in `LANGUAGES` of
  `js/i18n.js`) and one more `messages_<code>.properties`; `TranslationsTest` flags any
  missing key.
