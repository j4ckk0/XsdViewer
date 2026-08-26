# XsdViewer

A small tool to explore an XML Schema (`.xsd`) file in the browser.

A Java server parses the schema and serves a web page offering two views:

- **Graph** – the global objects of the schema (elements, complex types, simple types,
  groups, attribute groups, attributes) and their *level-1* links. The selected object
  sits in the middle, what it links to is on the right, what uses it is on the left.
  Click any node to make it the centre; **← Back** (or Alt+←) returns to the previous one.
- **Text** – the schema source with line numbers and syntax colouring. The selected
  object's declaration is highlighted; click a highlighted line number to select that
  object.

The file is opened with **File ▸ Open…** (Ctrl+O) or by dropping it anywhere in the window.

## Build and run

Requires Java 21 and Maven.

```bash
./run.sh                # builds target/xsdviewer.jar if needed, then starts the tool
```

or by hand:

```bash
mvn package
java -jar target/xsdviewer.jar
```

The server listens on <http://127.0.0.1:8080/> and opens it in the default browser.

```
./run.sh [--rebuild] [--port N] [--host H] [--no-browser] [file.xsd]
```

Passing a file on the command line opens it at start-up. `samples/purchaseOrder.xsd`
is a small schema exercising every kind of link.

## What counts as a link

For each global declaration, the links attributed to it are collected from its whole
content (anonymous nested types included):

| XSD construct | Link label |
|---|---|
| `type="T"` on the global element / attribute itself | `type` |
| `type="T"` on a nested element / attribute | `child name` / `attribute name` |
| `ref="X"` on an element / attribute | `child ref` / `attribute ref` (`ref` on a global element) |
| `group ref` / `attributeGroup ref` | `group` / `attributeGroup` |
| `extension base` / `restriction base` | `extends` / `restricts` |
| `list itemType` / `union memberTypes` | `list of` / `union of` |
| `substitutionGroup` | `substitutes` |

XSD built-in types (`xs:string`…) appear as grey dashed nodes (toggle with the
**built-in types** checkbox). Objects referenced but not declared in the file
(imported ones) appear as red dashed *external* nodes. Only the opened file is
read; `xs:import` / `xs:include` are listed in the sidebar but not followed.

## Layout

```
src/main/java/fr/j4ckk0/xsdviewer/
  Main.java        HTTP server (JDK com.sun.net.httpserver), static files + /api/parse
  XsdParser.java   XSD text -> Model (DOM for the structure, SAX for line numbers)
  Model.java       nodes / edges / imports, JSON output
  Json.java        string escaping
src/main/resources/web/   index.html, app.js, style.css – the client, no framework
src/test/java/            parser tests, run against samples/purchaseOrder.xsd
```

No runtime dependency: the jar only needs a JDK.
