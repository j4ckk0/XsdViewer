# xsdviewer-core

The library behind [XsdViewer](../README.md): it reads an XML Schema, a WSDL 1.1 or a Schematron
and gives back a graph of its declarations, their links and their content models; it writes that
graph as JSON; it validates a document against a schema and a Schematron. Java 21, the JDK and
nothing else. It is the Java module `org.jtools.xsdviewer.core`.

```xml
<dependency>
  <groupId>org.jtools</groupId>
  <artifactId>xsdviewer-core</artifactId>
  <version>5.0.0</version>
</dependency>
```

## Reading a schema

```java
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaParser;
import org.jtools.xsdviewer.schema.SchemaException;

String text = Files.readString(Path.of("purchaseOrder.xsd"));
try {
    SchemaGraph graph = SchemaParser.parse(text);          // an XSD, a WSDL or a Schematron: the root tag says which
    for (SchemaGraph.Node n : graph.nodes.values()) {      // one node per global declaration
        System.out.println(n.kind() + " " + n.name() + " lines " + n.line() + "-" + n.endLine());
    }
    for (SchemaGraph.Edge e : graph.edges) {               // one edge per direct link: from, to, label, cardinality
        System.out.println(e.from() + " --" + e.label() + "--> " + e.to());
    }
    String json = graph.toJson();                          // what the XsdViewer page reads (architecture.md: JSON model)
} catch (SchemaException e) {
    System.err.println(e.getMessage());                    // not XML, not a schema: why, in English or French
}
```

A node's `content()` and `attributes()` hold its content model — the particles (sequence, choice,
all, element, group reference, wildcard) and the attributes an editor would draw — and `line()` /
`endLine()` where its declaration is written. The vocabularies of the graph are constants:
`NodeKind` (the kinds of node), `LinkLabel` (the words on the edges), `ParticleKind`.

## Validating a document

```java
import org.jtools.xsdviewer.schema.XmlValidator;
import org.jtools.xsdviewer.schema.SchematronValidator;

XmlValidator.Result xsd = XmlValidator.validate(Path.of("purchaseOrder.xsd"), xml);      // the JDK's validator, problems located
SchematronValidator.Result sch = SchematronValidator.validate(Path.of("rules.sch"), xml, null);   // the schema's default phase
```

Both answer a result with `valid()` and a list of located problems; `SchematronValidator` runs the
phases, patterns, rules and assertions with the JDK's XPath 1.0 engine and says what it could not
evaluate rather than skipping it silently.

## Errors and messages

`SchemaException` is the one checked exception of the parsers and validators: its message says what
is wrong, in the language `Messages` is set to — the JVM's by default, or the one given to
`Messages.setRequestLocale()` for the current thread, which is how the XsdViewer server answers each
request in the language of its page. English and French are provided.
