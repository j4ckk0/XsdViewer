# XsdViewer

[![build](https://github.com/j4ckk0/XsdViewer/actions/workflows/build.yml/badge.svg)](https://github.com/j4ckk0/XsdViewer/actions/workflows/build.yml)

A small tool to explore an XML Schema (`.xsd`) file in the browser — and the WSDL 1.1
(`.wsdl`) services built on such schemas, and the Schematron (`.sch`) rules written over them.

A Java server parses the schema and serves a web page offering two views:

- **Graph** – the global objects of the schema (elements, complex types, simple types,
  groups, attribute groups, attributes) and their *level-1* links. The selected object
  sits in the middle, what it links to is on the right, what uses it is on the left.
  **2 levels** adds a column on the right: what each linked object links to in turn,
  drawn as trees (e.g. complexType → element type → its own types and attributes); the
  left side, what uses the selected object, stays one step deep. The other files of the workspace take part, open in
  a tab or only listed in the Files panel (they are parsed in the background): an external
  object declared elsewhere shows as what it is there (its real kind, its file) and is expanded
  from there at the second level, and objects of other files that use the centre appear on the
  left, marked with their file; clicking one of them switches to that file's tab, opening it when
  needed. The details panel lists those users too.
  Each link shows its **cardinality** after its name (`items 1..*`, `orderDate 0..1`): the
  `minOccurs`/`maxOccurs` of a nested element or group reference — through the enclosing
  `sequence`/`all`/`choice`, counted from the nearest enclosing element — or the `use` of an
  attribute. **Optional** links (minimum 0: `minOccurs="0"`, optional attribute, branch of a
  `choice`) are drawn dashed and lighter; mandatory ones solid. A **derivation** (`extends`,
  `restricts`: a type to its base type) ends with a hollow arrowhead, as a UML generalisation,
  where a content link (a nested element, a `ref`, a `type`) ends with a filled one.
  Click any node to make it the centre; **← Back** (or Alt+←) returns to the previous one. The
  graph works from the keyboard too: Tab into it, the arrow keys walk the nodes, Home is the
  centre, Enter or Space acts as a click. **File ▸ Open all listed files** opens in tabs the files
  a large folder left listed only.
- **Text** – the schema source with line numbers and syntax colouring. The selected
  object's declaration is highlighted; click a highlighted line number to select that
  object.

Files are opened with **File ▸ Open…** (Ctrl+O) or by dropping them anywhere in the window.
Each file lives in its own **tab** (tab bar under the top bar; **+** or File ▸ New tab
opens an empty one, × or a middle click closes one); every tab keeps its own view,
selection, history and search filter. The **search box** at the top of the left panel (Ctrl+F)
filters the Files panel and the object list below it — every schema of the workspace, open in a tab or
not, is searched, by the objects' names but also by the names of the elements and attributes inside
a declaration (a message's parts) and by the documentation, the reason being shown in grey after a
listed object; **File ▸ Validate an XML file…** checks a
document against the shown schema, in a tab of its own (see *Validation* below). **⤓ PNG** in the top bar saves the current view (graph or
text) as a PNG image, **⤓ SVG** the graph as a vector image (for documents). In the Text view, a
**find bar** (top right; Ctrl+F there) marks the lines holding a text and walks them with Enter / Shift+Enter. **File ▸ Quit** stops the server and closes the page.

### Validation

**File ▸ Validate an XML file…** (or an `.xml` file dropped on a schema's tab) checks a document
against the shown schema and opens the outcome in a tab named `order.xml ⇢ purchaseOrder.xsd`:

- against an **XSD** with the JDK's validator, from the schema's file on disk (imports included, so
  the schema needs a location: a file opened through the server's dialog or a folder);
- against a **Schematron** with the server's own evaluator over the JDK's XPath 1.0 engine: phases
  (the schema's `defaultPhase`, or the one picked in the tab's *phase* list), abstract patterns
  and their parameters, abstract rules (`extends`), `let` variables, `include`s next to the file,
  the messages with their `value-of` and `name` filled in and the `diagnostics` they name appended.
  Each node fires the first rule of a pattern whose context matches it, as ISO Schematron says. A
  test the engine cannot compile — XPath 2 and later, such as `xs:date()` — is listed once as *not
  evaluated* rather than passed silently (which is why the sample's date rule compares the dates
  as numbers, their dashes removed, instead of calling `xs:date()`);
- against **both** when the workspace holds the other one too (a located `.sch` next to the XSD, or
  the reverse). The tab's header holds one list per kind, *XSD* and *SCH*, with every file of that
  kind the workspace knows with a location on disk (and *none*, as long as the other kind stays):
  picking another checks the document again, and each list carries its own verdict.

The problems are listed on the left with their line (and column for the XSD's), the document on
the right with the lines marked; clicking a problem or a marked line shows the other, the arrow
keys walk the list. A Schematron problem names the assertion, rule and pattern that fired: each is
a link that selects it in the Schematron's tab. *errors only* hides the warnings (an assertion
with a `role` or `flag` naming a warning), the informative reports and what could not be evaluated;
the document's path is shown next to the verdict when the server knows it (a file opened in the
browser comes without its folder); *↻ Run again* validates again (the document read again from disk
when its path is known, the schemas always), *Another XML document…* keeps the schemas.
`samples/purchaseOrder.xml` is a document valid against `samples/purchaseOrder.xsd` and passing
every rule of `samples/schematron/purchaseOrder.sch`; its header comment says what to change to
see the rules fire.

## Screenshots

**Graph view** — `CatalogType` in the centre, what it links to on the right (two levels:
`publisher` is an `Address` from `common.xsd`, expanded from its own tab), what uses it on the
left; cardinalities after each link, optional links dashed. The details panel on the right
lists the links and the documentation — and, for an enumeration (a simpleType, an element or an
attribute restricted to `xs:enumeration` values), the values with their own documentation.

![Graph view](screenshots/XsdViewer-graph-view.jpg)

**Text view** — the same schema as source, the selected object's declaration highlighted;
clicking a line number selects that object.

![Text view](screenshots/XsdViewer-xml-view.jpg)

**Comparing two workspaces** — `v1` against `v2` of the same schema set: file by file, the
declarations and links only on one side, then the two sources side by side.

![Compare view](screenshots/XsdViewer-compare-view.jpg)

## Build and run

Requires a JDK 21 and Maven (see [Installing Java 21](#installing-java-21)).

```bash
scripts/run.sh                # builds target/xsdviewer.jar if needed, then starts the tool
scripts\run.bat               # same, on Windows
```

To only build the jar (`scripts/build.sh` / `scripts\build.bat`, i.e. `mvn package`), or by hand:

```bash
mvn package
java -jar target/xsdviewer.jar
```

The server listens on <http://127.0.0.1:8080/> and opens it in the default browser.

```
scripts/run.sh [--rebuild] [--port N] [--host H] [--no-browser] [--keep-alive] [file.xsd]   # Linux/macOS
scripts\run.bat  [--rebuild] [--port N] [--host H] [--no-browser] [--keep-alive] [file.xsd]   # Windows
```

Passing a file on the command line opens it at start-up. `samples/purchaseOrder.xsd`
is a small schema exercising every kind of link.

**The server stops by itself** 15 seconds after the last page showing it has been closed —
no orphan process left behind when you close the browser (*File ▸ Quit* stops it at once).
Each page holds a connection open for its whole life; the server only counts a page gone when
that connection breaks, so an idle page, even for hours and even in a background tab, keeps
the server up; a reload, a browser restart or a laptop waking up reconnect within the grace.
**Settings ▸ Stop the server when the last page is closed** switches it off (and on again)
from the page — the choice is kept for the next runs (in the user's Java preferences) — for a
server you open pages on now and then, or that other computers reach (`--host 0.0.0.0`).
On the command line, `--keep-alive` turns it off for a run, and `--no-browser` implies it (you
start it without a page and will open one later). **Settings ▸ Dark theme** / **Light theme** flips the
page's colours (the system's light or dark setting, followed as it changes, until you choose);
the browser remembers it, and the PNG export takes the page's background. One caveat:
Chrome's and Edge's *Memory Saver* may *discard* a background tab after a long idle time, which
is indistinguishable from closing it — the visible tab is never discarded; if the tool lives in
a background tab for hours, add `127.0.0.1` to *Settings ▸ Performance ▸ Always keep these sites
active*, or use `--keep-alive`.

## Installing Java 21

Only needed to run the jar (`xsdviewer-<version>.jar`) or to build from source. The Windows
zip and the Linux tarball bring their own JRE: unpack them anywhere and start the launcher,
nothing to install.

**What to get.** A *JRE* is enough to run the jar; a *JDK* is needed to build. Eclipse Temurin
is the free, vendor-neutral build used here: <https://adoptium.net/temurin/releases/?version=21>
— pick your OS and architecture, version 21 (LTS). Any other OpenJDK 21 (Microsoft, Amazon
Corretto, Oracle, your distribution's package) works the same.

**Windows.** Either the `.msi` installer — tick *Add to PATH* and *Set JAVA_HOME variable* in
the *Custom Setup* screen — or the `.zip`: unzip it into a folder without spaces, e.g.
`C:\Java\jdk-21`, then either add `C:\Java\jdk-21\bin` to the `Path` of your user
(*Settings ▸ System ▸ About ▸ Advanced system settings ▸ Environment Variables*, log off and on
again), or call it with its full path, no PATH change needed:

```bat
"C:\Java\jdk-21\bin\java" -jar xsdviewer-3.6.0.jar
```

**Linux.** Your distribution's package is the simplest — `sudo apt install openjdk-21-jre`
(Debian/Ubuntu; `openjdk-21-jdk` to build), `sudo dnf install java-21-openjdk` (Fedora/RHEL) —
otherwise the Temurin `.tar.gz`: unpack it under `/opt` (system-wide) or `~/java` (your user
only) and either put its `bin` on the PATH in `~/.profile` or `~/.bashrc`,

```bash
sudo tar xzf OpenJDK21U-jre_x64_linux_hotspot_*.tar.gz -C /opt   # → /opt/jdk-21.0.12.1+1-jre (name varies with the version)
export PATH=/opt/jdk-21.0.12.1+1-jre/bin:$PATH                     # in ~/.profile to make it permanent
```

or call it with its full path: `/opt/jdk-21.0.12.1+1-jre/bin/java -jar xsdviewer-3.6.0.jar`.

**macOS.** The Temurin `.pkg` installer, or `brew install --cask temurin@21`.

**Check.** A new terminal, then:

```
java -version
openjdk version "21.0.12" ...
```

If it reports another major version, an older Java is first on the PATH: run the jar with the
full path of the Java 21 `java` as above (under an older Java the jar fails at once with
`UnsupportedClassVersionError … class file version 65.0`, which means exactly that). To build,
Maven uses the JDK of `JAVA_HOME` when it is set, the `java` of the PATH otherwise.

**For packaging only** (`scripts/package.sh`), the JRE *archives* are not installed but copied
as they are into `jre/` — see [Packaging](#packaging).

## Packaging

```bash
scripts/package.sh            # or scripts\package.bat on Windows; runs: mvn package -Pdist
```

builds self-contained distributions that need no Java installed, each with a **trimmed runtime**
made with `jlink` from a Temurin JDK 21 — the modules the tool needs, about a third of a full JRE
(Eclipse Temurin, redistributed under the GPLv2 with Classpath Exception; its notices stay in
`jre/legal`) — and a launcher taking the same options as above (the archives of previous builds,
whatever their version, are deleted from `releases/` first):

| Archive | Launcher |
|---|---|
| `releases/xsdviewer-<version>-windows.zip` | `XsdViewer.exe` — double-click it (or drop an `.xsd` / workspace file on it, or run `XsdViewer.exe --port 9090 some.xsd`): starts the server with the bundled runtime, no console window at all. The exe is built with launch4j from any OS. `xsdviewer.bat` does the same from a command line (a `.bat` briefly flashes a console); `xsdviewer.bat --console …` keeps the console, with the server's messages |
| `releases/xsdviewer-<version>-linux.tar.gz` | `xsdviewer.sh` |
| `releases/xsdviewer-<version>-macos.tar.gz` | `xsdviewer.sh` — Apple silicon; a downloaded archive is quarantined by macOS, so once: `xattr -dr com.apple.quarantine xsdviewer-<version>` |
| `releases/xsdviewer-<version>.jar` | copy of `target/xsdviewer.jar`, for people who have [Java 21](#installing-java-21): `java -jar xsdviewer-<version>.jar` |

When started without a console (the Windows launcher, a double-clicked jar), a start-up
failure such as a port already in use is shown in a dialog instead of being lost.

A pushed tag `v<version>` makes GitHub Actions build and publish the release (`release.yml`: the
runtimes, the archives, the notes from `CHANGELOG.md`'s section for that version, the checksums).
By hand, `scripts/release.sh <version>` does the same from `releases/` (`--dry-run` prints the
notes, `--draft` creates a draft); it reads a GitHub token from `$GITHUB_TOKEN` or
`~/.config/github/xsdviewer-release-token` — see `PUBLISHING.md`.

The JDKs are not tracked in git: before packaging, download the Temurin **JDK** 21 archives
(a JDK, for its `jmods`; a JRE has none) from <https://adoptium.net/temurin/releases/> and put
them in `jre/` at the root of the project:

```
jre/
├── OpenJDK21U-jdk_x64_windows_hotspot_<version>.zip
├── OpenJDK21U-jdk_x64_linux_hotspot_<version>.tar.gz
└── OpenJDK21U-jdk_aarch64_mac_hotspot_<version>.tar.gz
```

Only the platforms whose archive is there are built; the archive of the machine doing the build
is required, since its `jlink` links every runtime (download them together: they must be the same
version). `src/build/runtimes.xml` (Ant, driven by the `dist` profile) does the unpacking, the
linking and the packing. Extra arguments (e.g. `-DskipTests`) are passed to `mvn` by all four scripts.

`scripts/screenshots.py` is a visual smoke test: with the jar built and Firefox installed, it opens
the samples, drives the page (a selection, the text view, two levels, the dark theme), checks a few
facts on it and saves a screenshot of each scene in `target/screenshots/`.

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
| `keyref refer="K"` | `keyref name`, to the element declaring the key `K` |

`xs:any` and `xs:anyAttribute` are not links: they appear among a declaration's *members* (what
the search sees) as `any (##other)`, with their namespace constraint.

A **WSDL 1.1** file (`wsdl:definitions`) gets five more kinds of object — *service*,
*portType*, *operation*, *binding*, *message* —, listed in the legend when such a file is
shown, and the schemas inline in its `wsdl:types` are parsed as if they were the file's own
(their `xs:import`s are followed like a schema's). The links follow the chain from the service
to the schema objects, so that **2 levels** from an operation reaches the elements its messages carry:

| WSDL construct | Link label |
|---|---|
| `service/port binding="B"` | the port's name, from the service to the portType that `B` binds (to `B` itself when it is declared elsewhere) |
| `portType/operation` | `operation` |
| `operation/input`, `output`, `fault` `message="M"` | `input` / `output` / `fault` |
| `message/part element="E"` or `type="T"` | the part's name |
| `binding type="P"` | `binds` |

An operation is named within its portType (`operation:Orders.submit` as an id); a WSDL opens on
its first service. `samples/wsdl/purchaseOrderService.wsdl` is a small example over `samples/purchaseOrder.xsd`.

A **Schematron** file (`sch:schema` in the ISO namespace, or in the older Schematron 1.5 one; a
fragment meant for `sch:include` — a `pattern`, a `rule` — is read as if it were the only child of a
schema) has six kinds of object of its own — *phase*, *pattern*, *rule*, *assert*, *report*,
*diagnostic* —, the only ones in the legend then. Schematron names little: a rule is known by its
`context`, an assertion by its `test`. So a node is named by its `id` when it has one and by its
expression otherwise (a pattern by its `id`, its `name` or its `title`, else its rank), and the
expression is shown whole in a box at the top of the details panel, which the search box also
searches; the message of an assertion or diagnostic is its documentation, its `role` or `flag`
first in brackets, a `value-of` shown as `{select}`, a `name` as `{name()}`. The links:

| Schematron construct | Link label |
|---|---|
| `phase/active pattern="P"` | `active` |
| `pattern/rule` | `rule` |
| `pattern is-a="A"` | `is a`, to the abstract pattern (a derivation: hollow arrowhead) |
| `rule/extends rule="R"` | `extends`, to the abstract rule (a derivation) |
| `rule/assert`, `rule/report` | `assert` / `report` |
| `assert diagnostics="D1 D2"` | `diagnostic`, one link per diagnostic |
| `include href="…"` | listed with the imports; a pattern, rule or diagnostic named but not declared in the file is an *external* node |

A Schematron opens on its first phase, else its first pattern; **2 levels** from a pattern shows
its rules and their assertions. `samples/schematron/purchaseOrder.sch` is an example over
`samples/purchaseOrder.xsd`; documents are checked against it with **File ▸ Validate an XML file…**
(see *Validation* above).

XSD built-in types (`xs:string`…) appear as grey-filled nodes with a grey border (toggle with
the **built-in types** checkbox). Objects referenced but not declared in the file (imported /
included ones) appear as grey-filled *external* nodes with a red border. Dashed lines are
reserved for optional links, hollow arrowheads for derivations (`extends`, `restricts`, `is a`).

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
machine: **File ▸ Open…** (Ctrl+O) therefore goes through the server's own file dialog — the
native one on Windows and macOS; on Linux the desktop's, through `kdialog` (KDE, LXQt) or `zenity`
(GNOME and others) when one is installed, else a Swing chooser in the system look and feel —
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
object; the ⧉ button of a folder row (on hover) opens that sub-folder as a workspace of its own.
While the search box holds a text, the panel lists only the objects whose name contains it, in
the files holding one — a way to find a type across a whole folder without opening its files.
The ⊞ / ⊟ buttons expand or collapse a whole tree at once (the Files panel, the object list,
the rows of a comparison). Large sets stay light: a folder or a workspace holding more than 10 schemas, or more
than 10 linked schemas found at once, are only listed (parsed in the background, a few at a time —
while a search runs before they are all parsed, a last row counts the files it cannot see yet) and a single
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

A workspace file can also be given on the command line: `scripts/run.sh samples/all.xsdviewer.json`.
Opening and saving need the server's dialogs, i.e. a display.

### Comparing two workspaces

**Ctrl+click** two workspace chips to select them (orange ring), then **⇄ Compare** in the tab
bar: a folder-comparison view lists every file the two workspaces know — open in a tab or
only listed — **paired by name**, each
marked *identical* (same text, line endings aside), *different*, or *only in* one of them, with
a summary line. A *different* row expands to what changed in the schema — declarations and
links (cardinality included) present on one side only — and to a side-by-side line
comparison (long identical stretches folded). A block of lines moved elsewhere is recognised
(two lines or more, or one telling line): it is shown in blue on both sides with "moved
to / from line N" instead of red and green, and a file whose text differences are all moves
gets the status *moved lines only*, counted apart; a legend of the three colours sits in the
header of the comparison. **Business lines only** (on by default,
remembered) ignores what does not define the schema — XML comments, `xs:annotation` blocks,
the XML declaration, the `xs:schema`, `xs:import` and `xs:include` tags, blank lines and
indentation — both for the status and in the line comparison, which keeps the original line
numbers. Each side of the line comparison scrolls sideways on its own when a line is long. **Differences only** hides the identical files and reduces a file's
comparison to its changed lines with one line of context. The comparison is a tab of its own
(`v1 ⇄ v2`) in the workspace it was started from: switch to and from it like any tab, close
it with its `×`; closing one of the compared workspaces closes it too, and comparing the same
pair again brings it back to front. **One file's differences in a tab**: the *⧉ In a tab* button
next to the status of a *different* or *moved lines only* row, or a double-click on
the row, opens that pair's differences in their own tab (`product.xsd (v1 ⇄ v2)`), next to the
comparison, with the whole page's height; the two options apply there too, and opening the same
pair again brings its tab to front. To try it: `scripts/run.sh samples/compare/v1.xsdviewer.json`,
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
     the browser (their `.xsd` / `.wsdl` / `.sch` / `.xml` files are kept at hand), then
   - on disk by the server, relative to the current file when it knows where it is: the
     file given on the command line, every file reached from it, and files opened from
     the browser that the server managed to locate (a browser hides the folder of a
     file it opens, so the server looks for a file with the same name and content under
     the folders it already knows and its working directory), else relative to those
     folders;
3. else a file chooser opens, with a message naming the wanted file; once you pick it,
   the link is followed.

Remote `schemaLocation`s (`http://…`) are never fetched. `samples/import/` is a
schema split over four files to try this with: `scripts/run.sh samples/import/order.xsd`,
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
                         FileDialogs (native dialog; kdialog / zenity on Linux)
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

No runtime dependency: the jar only needs a Java 21 runtime.

See [architecture.md](architecture.md) for the modules, the data flow and the libraries used.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE). Copyright 2026 jtools.org.
`samples/purchaseOrder.xsd` is adapted from the W3C *XML Schema Part 0: Primer* example
(W3C Document License). The distributions bundle an Eclipse Temurin JRE (GPLv2 with
Classpath Exception).
