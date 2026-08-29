# XsdViewer

A small tool to explore an XML Schema (`.xsd`) file in the browser.

A Java server parses the schema and serves a web page offering two views:

- **Graph** – the global objects of the schema (elements, complex types, simple types,
  groups, attribute groups, attributes) and their *level-1* links. The selected object
  sits in the middle, what it links to is on the right, what uses it is on the left.
  **2 levels** adds a column on the right: what each linked object links to in turn,
  drawn as trees (e.g. complexType → element type → its own types and attributes); the
  left side, what uses the selected object, stays one step deep. Objects of the other open tabs take part: an external
  object whose file is open is expanded from that tab (its box shows the real kind and
  the file), and objects of other files that use the centre appear on the left, marked
  with their file; clicking one of them switches to that tab.
  Each link shows its **cardinality** after its name (`items 1..*`, `orderDate 0..1`): the
  `minOccurs`/`maxOccurs` of a nested element or group reference — through the enclosing
  `sequence`/`all`/`choice`, counted from the nearest enclosing element — or the `use` of an
  attribute. **Optional** links (minimum 0: `minOccurs="0"`, optional attribute, branch of a
  `choice`) are drawn dashed and lighter; mandatory ones solid.
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
bundled JRE 21 (Eclipse Temurin, redistributed under the GPLv2 with Classpath Exception; its
notices stay in `jre/legal`) and a launcher taking the same options as above (the archives of
previous builds, whatever their version, are deleted from `target/` first):

| Archive | Launcher |
|---|---|
| `target/xsdviewer-<version>-windows.zip` | `XsdViewer.exe` — double-click it (or drop an `.xsd` / workspace file on it, or run `XsdViewer.exe --port 9090 some.xsd`): starts the server with the bundled JRE, no console window at all. The exe is built with launch4j from any OS. `xsdviewer.bat` does the same from a command line (a `.bat` briefly flashes a console); `xsdviewer.bat --console …` keeps the console, with the server's messages |
| `target/xsdviewer-<version>-linux.tar.gz` | `xsdviewer.sh` |

When started without a console (the Windows launcher, a double-clicked jar), a start-up
failure such as a port already in use is shown in a dialog instead of being lost.

The JREs are not tracked in git: before packaging, download the Temurin JRE 21
archives from <https://adoptium.net/temurin/releases/> and put them in
`jre/` at the root of the project:

```
jre/
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

XSD built-in types (`xs:string`…) appear as grey-filled nodes with a grey border (toggle with
the **built-in types** checkbox). Objects referenced but not declared in the file (imported /
included ones) appear as grey-filled *external* nodes with a red border. Dashed lines are
reserved for optional links.

**Help ▸ About XsdViewer…** shows the version (from the jar's manifest), the Java runtime, the
log file, the licence and the project page.

## Logs

The server logs what it does and what fails on its console and in `xsdviewer.0.log` in the
system's temporary directory (`/tmp` on Linux, `%TEMP%` on Windows; two rotating files of
1 MB — the path is shown in Help ▸ About and printed at start-up). A request that fails is
logged with its stack trace and answered to the page as an error message, so a problem shows
both in the page (toast) and in the log.

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

The **Files** panel at the top of the sidebar lists every file the active workspace knows as a
tree by folder (the folders they all share are left out) — open in a tab (bold) or only listed
—, the shown file highlighted; each file unfolds to its global objects (the ▸ before it).
Clicking a file or one of its objects shows its tab, opening it when needed, and selects the
object; the ⧉ button of a folder row (on hover) opens that sub-folder as a workspace of its own. Large sets stay light: a folder or a workspace holding more than 10 schemas, or more
than 10 linked schemas found at once, are only listed (parsed in the background) and a single
tab is opened; the others open on demand from the panel.

A **workspace is a group of tabs**: the workspace bar, above the tabs, shows one chip per
workspace (click it to switch to that workspace — the tab you last had there comes back —,
its `×` closes it with all its tabs), and the tab bar shows the tabs of the active workspace
only. Every tab belongs to a
workspace — the first one is "Workspace 1" until it is saved — and a workspace is a closed
world: links are followed, linked schemas auto-loaded and "used by" looked up among its own
tabs only, so two workspaces holding the same file do not see each other (that is what makes
them comparable, later). The File menu:

- **Open folder…** — lists every `.xsd` of a folder and its whole sub-tree (symbolic links
  followed, up to 2000 files) as a
  workspace named after the folder (through the server's folder chooser; the browser's when
  the server has no display), opening them all when there are at most 10, else the first one.
  Dropping a folder on the window does the same.
- **New workspace** — an empty group of tabs, made active.
- **Open workspace…** — opens a `<name>.xsdviewer.json` as a new group next to the workspaces
  already open (an empty unsaved one is taken over; a workspace already open is brought to
  front; missing files are reported). Several workspaces can be open together.
- **Save workspace…** (Ctrl+S) — writes the active workspace: the location of its files
  (relative to the workspace file when they share its root) and which tab is shown; files
  whose location is unknown are left out and named in the message. The workspace's own file
  is proposed; the workspace takes the name of the file.
- **Close workspace** — the active workspace and its tabs; **Close all tabs** closes every workspace.

A workspace file can also be given on the command line: `./run.sh samples/all.xsdviewer.json`.
Opening and saving need the server's dialogs, i.e. a display.

### Comparing two workspaces

**Ctrl+click** two workspace chips to select them (orange ring), then **⇄ Compare** in the tab
bar: a folder-comparison view lists the files of both workspaces **paired by name**, each
marked *identical* (same text, line endings aside), *different*, or *only in* one of them, with
a summary line. A *different* row expands to what changed in the schema — declarations and
links (cardinality included) present on one side only — and to a side-by-side line
comparison (long identical stretches folded). A block of lines moved elsewhere is recognised
(two lines or more, or one telling line): it is shown in blue on both sides with "moved
to / from line N" instead of red and green, and a file whose text differences are all moves
gets the status *moved lines only*, counted apart. **Business lines only** (on by default,
remembered) ignores what does not define the schema — XML comments, `xs:annotation` blocks,
blank lines and indentation — both for the status and in the line comparison, which keeps the
original line numbers. **Differences only** hides the identical files and reduces a file's
comparison to its changed lines with one line of context. The comparison is a tab of its own
(`v1 ⇄ v2`) in the workspace it was started from: switch to and from it like any tab, close
it with its `×`; closing one of the compared workspaces closes it too, and comparing the same
pair again brings it back to front. To try it: `./run.sh samples/compare/v1.xsdviewer.json`,
then File ▸ Open workspace… `samples/compare/v2.xsdviewer.json` (what differs is listed in
`samples/compare/README.md`).

## Following links into other files

Selecting an external node looks for its declaration, without asking whenever the file
can be found:

1. in the other open tabs (same name and namespace);
2. else in the file(s) named by the `xs:import` (matching namespace) / `xs:include`
   / `xs:redefine` of the current file, following their own imports and includes;
   each file found is opened in a new tab and the declaration selected there. A
   location is looked up
   - in the folders opened with **File ▸ Open folder…** or dropped on the window through
     the browser (their `.xsd` / `.xml` files are kept at hand), then
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
samples/                  purchaseOrder.xsd (one file), import/ (order.xsd + imported / included files),
                          compare/ (two versions of a schema set, v1 and v2, with a workspace each: see its README)
```

The page is shown in the language chosen in the drop-list at the right of the top bar
(remembered by the browser; initially the machine's language when a `web/i18n/<language>.json`
exists, English otherwise; `?lang=fr` forces one). The server answers the page in that language
too; only its console messages follow the JVM locale.

No runtime dependency: the jar only needs a JDK.

See [architecture.md](architecture.md) for the modules, the data flow and the libraries used.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE). Copyright 2026 jtools.org.
`samples/purchaseOrder.xsd` is adapted from the W3C *XML Schema Part 0: Primer* example
(W3C Document License). The distributions bundle an Eclipse Temurin JRE (GPLv2 with
Classpath Exception).
