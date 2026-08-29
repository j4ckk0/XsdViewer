# XsdViewer

A small tool to explore an XML Schema (`.xsd`) file in the browser.

A Java server parses the schema and serves a web page offering two views:

- **Graph** – the global objects of the schema (elements, complex types, simple types,
  groups, attribute groups, attributes) and their *level-1* links. The selected object
  sits in the middle, what it links to is on the right, what uses it is on the left.
  **2 levels** adds a column on each side: what each linked object links to in turn,
  and what uses each user in turn, drawn as trees (e.g. complexType → element type →
  its own types and attributes). Objects of the other open tabs take part: an external
  object whose file is open is expanded from that tab (its box shows the real kind and
  the file), and objects of other files that use the centre appear on the left, marked
  with their file; clicking one of them switches to that tab.
  Click any node to make it the centre; **← Back** (or Alt+←) returns to the previous one.
- **Text** – the schema source with line numbers and syntax colouring. The selected
  object's declaration is highlighted; click a highlighted line number to select that
  object.

Files are opened with **File ▸ Open…** (Ctrl+O) or by dropping them anywhere in the window.
Each file lives in its own **tab** (tab bar under the top bar; **+** or File ▸ New tab
opens an empty one, × or a middle click closes one); every tab keeps its own view,
selection, history and search filter. The **search box** in the left panel (Ctrl+F)
filters the object list; **⤓ PNG** in the top bar saves the current view (graph or
text) as a PNG image. **File ▸ Quit** stops the server and closes the page.

## Build and run

Requires Java 21 and Maven.

```bash
./run.sh                # builds target/xsdviewer.jar if needed, then starts the tool
run.bat                 # same, on Windows
```

To only build the jar (`./build.sh` / `build.bat`, i.e. `mvn package`), or by hand:

```bash
mvn package
java -jar target/xsdviewer.jar
```

The server listens on <http://127.0.0.1:8080/> and opens it in the default browser.

```
./run.sh [--rebuild] [--port N] [--host H] [--no-browser] [file.xsd]   # Linux/macOS
run.bat  [--rebuild] [--port N] [--host H] [--no-browser] [file.xsd]   # Windows
```

Passing a file on the command line opens it at start-up. `samples/purchaseOrder.xsd`
is a small schema exercising every kind of link.

## Packaging

```bash
./package.sh            # or package.bat on Windows; runs: mvn package -Pdist
```

builds two self-contained distributions that need no Java installed, each with a
bundled JRE 21 and a launcher taking the same options as above (the archives of previous
builds, whatever their version, are deleted from `target/` first):

| Archive | Launcher |
|---|---|
| `target/xsdviewer-<version>-windows.zip` | `xsdviewer.bat` — runs the server in the background (`javaw.exe`), so no console window stays open; `xsdviewer.bat --console …` keeps one, with the server's messages |
| `target/xsdviewer-<version>-linux.tar.gz` | `xsdviewer.sh` |

When started without a console (the Windows launcher, a double-clicked jar), a start-up
failure such as a port already in use is shown in a dialog instead of being lost.

The JREs are not tracked in git: before packaging, download the Temurin JRE 21
archives from <https://adoptium.net/temurin/releases/> and put them in
`src/main/resources/embedded/jre/`:

```
src/main/resources/embedded/jre/
├── OpenJDK21U-jre_x64_windows_hotspot_<version>.zip
└── OpenJDK21U-jre_x64_linux_hotspot_<version>.tar.gz
```

The build picks the `*windows*.zip` and `*linux*.tar.gz` found there, so upgrading
is just replacing the archives. Extra arguments (e.g. `-DskipTests`) are passed to
`mvn` by all four scripts.

## What counts as a link

For each global declaration, the links attributed to it are collected from its whole
content (anonymous nested types included):

| XSD construct | Link label |
|---|---|
| `type="T"` on the global element / attribute itself | `type` |
| `type="T"` on a nested element / attribute | `name` (the element's name) / `attribute name` |
| `ref="X"` on an element / attribute | `ref` / `attribute ref` |
| `group ref` / `attributeGroup ref` | `group` / `attributeGroup` |
| `extension base` / `restriction base` | `extends` / `restricts` |
| `list itemType` / `union memberTypes` | `list of` / `union of` |
| `substitutionGroup` | `substitutes` |

XSD built-in types (`xs:string`…) appear as grey dashed nodes (toggle with the
**built-in types** checkbox). Objects referenced but not declared in the file
(imported / included ones) appear as red dashed *external* nodes.

## Where the files are

A browser never tells a page where a chosen file sits on disk, but the server runs on the same
machine: **File ▸ Open…** (Ctrl+O) therefore goes through the server's own, native file dialog
whenever it has a display, and the files it returns come with their location. Without a
display (headless server), the browser's dialog is used and the server tries to locate the file
by name and content under the folders it already knows. Dropped files always go through the
browser.

Once a file's location is known, the schemas it links to — its `xs:import` / `xs:include` /
`xs:redefine` whose `schemaLocation` resolves **relative to that file** — are opened
automatically in background tabs, and theirs in turn (up to 50 files), so the graph shows
what uses what across the set right away.

### Workspaces

**File ▸ Save workspace…** (Ctrl+S) writes a `<name>.xsdviewer.json` holding the location of
the open files (relative to the workspace file when they share its root) and which tab is
shown; files whose location is unknown are left out and named in the message.
**File ▸ Open workspace…** replaces the open tabs with the workspace's files (missing ones are
reported). A workspace file can also be given on the command line:
`./run.sh samples/all.xsdviewer.json`. Both need the server's dialogs, i.e. a display.

## Following links into other files

Selecting an external node looks for its declaration, without asking whenever the file
can be found:

1. in the other open tabs (same name and namespace);
2. else in the file(s) named by the `xs:import` (matching namespace) / `xs:include`
   / `xs:redefine` of the current file, following their own imports and includes;
   each file found is opened in a new tab and the declaration selected there. A
   location is looked up
   - in the folders opened with **File ▸ Open folder…** or dropped on the window (all
     their `.xsd` files are kept at hand, nothing is opened until needed), then
   - on disk by the server, relative to the current file when it knows where it is: the
     file given on the command line, every file reached from it, and files opened from
     the browser that the server managed to locate (a browser hides the folder of a
     file it opens, so the server looks for a file with the same name and content under
     the folders it already knows and its working directory), else relative to those
     folders;
3. else a file chooser opens, with a message naming the wanted file; once you pick it,
   the link is followed.

Remote `schemaLocation`s (`http://…`) are never fetched. `samples/import/` is a
schema split over four files to try this with: `./run.sh samples/import/order.xsd`,
or start the tool from the project folder and open `order.xsd` from the browser.

## Layout

```
src/main/java/org/jtools/xsdviewer/
  XsdViewerApplication   entry point: command line, server start-up, browser
  CommandLineOptions, BrowserLauncher, Messages (+ MessageKey)
  schema/                XsdParser (XSD text -> SchemaGraph), DeclarationLineIndex, SchemaGraphJsonWriter,
                         NodeKind / LinkLabel / XsdVocabulary constants
  server/                XsdViewerServer (JDK com.sun.net.httpserver) + one handler per path:
                         ParseSchemaHandler, InitialFileHandler, OpenSchemaLocationHandler,
                         LocateSchemaFileHandler, ChooseFilesHandler, SaveWorkspaceHandler,
                         OpenWorkspaceHandler, CapabilitiesHandler, QuitHandler, StaticResourceHandler;
                         FileDialogs (native java.awt.FileDialog)
  workspace/             Workspace (the *.xsdviewer.json format)
  json/                  JsonWriter, JsonReader, JsonStrings, JsonKey
src/main/resources/org/jtools/xsdviewer/   messages.properties (English), messages_fr.properties – server texts
src/main/resources/web/   index.html, style.css, js/ (ES modules, one per concern), i18n/en.json, i18n/fr.json – the client, no framework
src/test/java/            parser, JSON, command line and translation tests (samples/purchaseOrder.xsd)
samples/                  purchaseOrder.xsd (one file), import/ (order.xsd + imported / included files)
```

The page is shown in the browser's language when a `web/i18n/<language>.json` exists (English
otherwise; `?lang=fr` forces one); the server's own messages follow the JVM locale.

No runtime dependency: the jar only needs a JDK.

See [architecture.md](architecture.md) for the modules, the data flow and the libraries used.
