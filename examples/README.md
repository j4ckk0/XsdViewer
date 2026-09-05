# Examples for developers

XsdViewer is two things a developer can build on: **`xsdviewer-core`**, a Java library with no
dependency but the JDK, and the **HTTP API** of its server. This folder shows both, on the files of
[`samples/`](../samples/README.md). The Java examples are a module of the build, so they compile and
run with every release (`ExamplesTest` runs each of them).

## The library — `src/main/java/org/jtools/xsdviewer/examples/`

| Program | What it shows |
|---|---|
| `ReadSchema <file>` | `SchemaParser.parse` on an XSD, a WSDL or a Schematron: the declarations with the lines they span, the links between them with their occurrences, the graph as JSON. A file that is not a schema is a `SchemaException` whose message says why. |
| `ModelOfDeclaration <kind:name> <file> [others…]` | `ContentTree.build` over a `Library` of files: the content model of a declaration as the Model view draws it, named types opened in place from whichever file declares them. |
| `CompareDeclarations <kind:name> <folder1> <folder2>` | `ModelDiff.mark` on two trees: the boxes marked same, changed, removed or added, matched by what they are — the Objects section of the comparison. |
| `ValidateDocument <xml> <xsd> <sch>` | `XmlValidator.validate` and `SchematronValidator.validate`: located problems, the Schematron's phases. |

Build once from the root (`mvn -q package -DskipTests`), then, from the root of the repository:

```sh
cp=examples/target/classes:core/target/classes
java -cp $cp org.jtools.xsdviewer.examples.ReadSchema samples/purchaseOrder.xsd
java -cp $cp org.jtools.xsdviewer.examples.ModelOfDeclaration complexType:PurchaseOrderType samples/purchaseOrder.xsd samples/ext.xsd
java -cp $cp org.jtools.xsdviewer.examples.CompareDeclarations complexType:ProductType samples/compare/v1 samples/compare/v2
java -cp $cp org.jtools.xsdviewer.examples.ValidateDocument samples/purchaseOrder.xml samples/purchaseOrder.xsd samples/schematron/purchaseOrder.sch
```

In a project of your own, the dependency is `org.jtools:xsdviewer-core` ([core/README.md](../core/README.md)).

## The HTTP API — `api/`

The server is a local tool: it binds to `127.0.0.1:8080` unless told otherwise, and answers the
page's requests, which any program can make too. Every call is stateless — the request carries the
files' texts — so each script is the whole story. Start the server, then run them from the root:

```sh
scripts/run.sh --no-browser --keep-alive        # or java -jar app/target/xsdviewer.jar --no-browser --keep-alive
examples/api/parse.sh samples/purchaseOrder.xsd
examples/api/model.sh complexType:PurchaseOrderType samples/purchaseOrder.xsd samples/ext.xsd
examples/api/compare-declarations.sh complexType:ProductType samples/compare/v1/product.xsd samples/compare/v2/product.xsd
examples/api/compare-texts.sh samples/compare/v1/product.xsd samples/compare/v2/product.xsd
examples/api/compare-workspaces.sh samples/compare/v1 samples/compare/v2
```

| Script | Endpoint | Answer |
|---|---|---|
| `parse.sh` | `POST /api/parse`, the file's text | the graph: `nodes` (with `content` and `attributes`), `edges`, `imports` |
| `model.sh` | `POST /api/model`, `{files, home, id, openAll}` | the content model tree, one box per particle |
| `compare-declarations.sh` | `POST /api/compare/declarations`, `{left, right}` sides | the two trees marked, the counts, the links only one side has |
| `compare-texts.sh` | `POST /api/compare/texts`, `{left, right, businessOnly}` | the lines of each side, the edit script, whether only blocks moved |
| `compare-workspaces.sh` | `POST /api/compare/workspaces`, `{left: [files], right: [files], businessOnly}` | the pairs by name with a status each |

The scripts need `curl` and `python3` (to build the JSON bodies); `XSDVIEWER_URL` points them at another
server. The bodies and answers are described in [architecture.md](../architecture.md#http-interface).
