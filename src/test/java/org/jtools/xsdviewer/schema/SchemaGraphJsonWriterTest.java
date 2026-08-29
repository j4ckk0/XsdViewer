package org.jtools.xsdviewer.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SchemaGraphJsonWriterTest {

    @Test
    void sampleSchemaIsWellFormed() throws Exception {
        String json = XsdParser.parse(Files.readString(Path.of("samples/purchaseOrder.xsd"))).toJson();
        assertTrue(json.startsWith("{\"targetNamespace\":\"http://example.com/po\",\"imports\":[],\"nodes\":[{\"id\":\"element:purchaseOrder\""));
        assertTrue(json.contains("\"from\":\"element:urgentComment\",\"to\":\"element:comment\",\"label\":\"substitutes\""));
        assertTrue(json.endsWith("}]}"));
        assertFalse(json.contains("\n"));
    }

    @Test
    void emptyGraph() {
        assertEquals("{\"targetNamespace\":\"\",\"imports\":[],\"nodes\":[],\"edges\":[]}", new SchemaGraph().toJson());
    }

    @Test
    void importsAndNodesAreWritten() {
        SchemaGraph g = new SchemaGraph();
        g.targetNamespace = "urn:t";
        g.imports.add(new SchemaGraph.Import("include", "", "b.xsd"));
        g.nodes.put("element:a", new SchemaGraph.Node("element:a", NodeKind.ELEMENT, "a", "urn:t", 3, "doc \"quoted\""));
        g.edges.add(new SchemaGraph.Edge("element:a", "builtin:string", LinkLabel.TYPE));
        assertEquals("{\"targetNamespace\":\"urn:t\","
                + "\"imports\":[{\"tag\":\"include\",\"namespace\":\"\",\"schemaLocation\":\"b.xsd\"}],"
                + "\"nodes\":[{\"id\":\"element:a\",\"kind\":\"element\",\"name\":\"a\",\"ns\":\"urn:t\",\"line\":3,\"doc\":\"doc \\\"quoted\\\"\"}],"
                + "\"edges\":[{\"from\":\"element:a\",\"to\":\"builtin:string\",\"label\":\"type\"}]}", g.toJson());
    }
}
