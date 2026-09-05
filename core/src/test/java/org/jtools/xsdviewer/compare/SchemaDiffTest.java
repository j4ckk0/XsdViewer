package org.jtools.xsdviewer.compare;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.jtools.xsdviewer.compare.SchemaDiff.Link;
import org.jtools.xsdviewer.model.Library.File;
import org.jtools.xsdviewer.schema.LinkLabel;
import org.jtools.xsdviewer.schema.NodeKind;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.jtools.xsdviewer.schema.SchemaGraph.Edge;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.junit.jupiter.api.Test;

class SchemaDiffTest {

    private static Node node(String id) {
        return new Node(id, SchemaGraph.kindOf(id), SchemaGraph.nameOf(id), "", 0, "");
    }

    private static SchemaGraph graph(List<Node> nodes, List<Edge> edges) {
        SchemaGraph g = new SchemaGraph();
        for (Node n : nodes) g.nodes.put(n.id(), n);
        g.edges.addAll(edges);
        return g;
    }

    @Test
    void identicalModelsAreTheSame() {
        SchemaGraph m = graph(List.of(node("element:a"), node("builtin:string")), List.of(new Edge("element:a", "builtin:string", LinkLabel.TYPE)));
        SchemaDiff.Result d = SchemaDiff.of(m, m);
        assertTrue(d.same());
        assertEquals(List.of(), d.nodesOnlyLeft());
    }

    @Test
    void placeholdersDoNotCountDeclarationsAndLinksDo() {
        SchemaGraph left = graph(List.of(node("element:a"), node("external:X")),
                List.of(new Edge("element:a", "external:X", LinkLabel.REF, new Cardinality(1, 1), "")));
        SchemaGraph right = graph(List.of(node("element:a"), node("element:b"), node("builtin:string")),
                List.of(new Edge("element:a", "external:X", LinkLabel.REF, new Cardinality(0, 1), "")));
        SchemaDiff.Result d = SchemaDiff.of(left, right);
        assertFalse(d.same());
        assertEquals(List.of(), d.nodesOnlyLeft());
        assertEquals(List.of("element:b"), d.nodesOnlyRight().stream().map(Node::id).toList());
        assertEquals(1, d.edgesOnlyLeft().size(), "the same link with another cardinality is another link");
        assertEquals(1, d.edgesOnlyRight().size());
    }

    @Test
    void aLinkIsKeyedByWhatItIsNotWhereItIsWritten() {
        Node string = node("builtin:string");
        Edge required = new Edge("complexType:T", "builtin:string", "name", new Cardinality(1, 1), "");
        assertEquals(SchemaDiff.linkKey(string, required), SchemaDiff.linkKey(string, new Edge("complexType:U", "builtin:string", "name", new Cardinality(1, 1), "")),
                "the owner is not part of the key");
        assertNotEquals(SchemaDiff.linkKey(string, required), SchemaDiff.linkKey(string, new Edge("complexType:T", "builtin:string", "name", new Cardinality(0, 1), "")),
                "a changed cardinality is another link");
    }

    @Test
    void theNeighbourhoodOfANodeHoldsItsLinksBothWays() {
        Node t = node("complexType:T"), e = node("element:e"), s = node("builtin:string");
        Edge out = new Edge(t.id(), s.id(), "name"), in = new Edge(e.id(), t.id(), LinkLabel.TYPE);
        File file = new File("a.xsd", graph(List.of(t, e, s), List.of(out, in)));
        Map<String, Link> links = SchemaDiff.neighbourhood(file, t.id());
        assertEquals(2, links.size());
        assertTrue(links.containsKey(SchemaDiff.linkKey(s, out)) && links.containsKey(SchemaDiff.linkKey(e, in)));
        assertEquals(new Link("name", NodeKind.BUILTIN, "string", null), links.get(SchemaDiff.linkKey(s, out)));
    }
}
