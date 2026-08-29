package org.jtools.xsdviewer.schema;

/*-
 * #%L
 * XsdViewer
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
        g.edges.add(new SchemaGraph.Edge("element:a", "builtin:int", "n", new SchemaGraph.Cardinality(0, SchemaGraph.Cardinality.UNBOUNDED)));
        assertEquals("{\"targetNamespace\":\"urn:t\","
                + "\"imports\":[{\"tag\":\"include\",\"namespace\":\"\",\"schemaLocation\":\"b.xsd\"}],"
                + "\"nodes\":[{\"id\":\"element:a\",\"kind\":\"element\",\"name\":\"a\",\"ns\":\"urn:t\",\"line\":3,\"doc\":\"doc \\\"quoted\\\"\"}],"
                + "\"edges\":[{\"from\":\"element:a\",\"to\":\"builtin:string\",\"label\":\"type\"},"
                + "{\"from\":\"element:a\",\"to\":\"builtin:int\",\"label\":\"n\",\"min\":0,\"max\":-1}]}", g.toJson());
    }
}
