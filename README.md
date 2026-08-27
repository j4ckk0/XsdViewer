# XsdViewer

A small tool to explore an XML Schema (`.xsd`) file in the browser.

A Java server parses the schema and serves a web page offering two views:

- **Graph** – the global objects of the schema (elements, complex types, simple types,
  groups, attribute groups, attributes) and their *level-1* links. The selected object
  sits in the middle, what it links to is on the right, what uses it is on the left.
  **2 levels** adds a further column: what each linked object links to in turn, drawn
  as a tree (e.g. complexType → element type → its own types and attributes). An
  external object whose file is open in another tab is expanded from that tab (its box
  shows the real kind and the file); clicking one of its children switches to that tab.
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
bundled JRE 21 and a launcher taking the same options as above:

| Archive | Launcher |
|---|---|
| `target/xsdviewer-<version>-windows.zip` | `xsdviewer.bat` |
| `target/xsdviewer-<version>-linux.tar.gz` | `xsdviewer.sh` |

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
src/main/java/fr/j4ckk0/xsdviewer/
  Main.java        HTTP server (JDK com.sun.net.httpserver), static files + /api/parse, /api/initial, /api/open
  XsdParser.java   XSD text -> Model (DOM for the structure, SAX for line numbers)
  Model.java       nodes / edges / imports, JSON output
  Json.java        string escaping
src/main/resources/web/   index.html, app.js, style.css – the client, no framework
src/test/java/            parser tests, run against samples/purchaseOrder.xsd
samples/                  purchaseOrder.xsd (one file), import/ (order.xsd + imported / included files)
```

No runtime dependency: the jar only needs a JDK.

See [architecture.md](architecture.md) for the modules, the data flow and the libraries used.
