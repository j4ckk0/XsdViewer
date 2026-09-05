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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jtools.xsdviewer.model.Library.File;
import org.jtools.xsdviewer.schema.NodeKind;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.jtools.xsdviewer.schema.SchemaGraph.Edge;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;

/**
 * What differs between two parsed schemas — the declarations and the links that exist on one side
 * only — and the keys telling links apart, which the comparison of two neighbourhoods uses.
 */
public final class SchemaDiff {

    /** Nothing a name, a kind or a label can hold, so the parts of a key cannot run into one another. */
    private static final String KEY_SEPARATOR = "\u0000";

    /** The declarations and links present in only one of two parsed models. */
    public record Result(List<Node> nodesOnlyLeft, List<Node> nodesOnlyRight, List<Edge> edgesOnlyLeft, List<Edge> edgesOnlyRight) {
        public boolean same() {
            return nodesOnlyLeft.isEmpty() && nodesOnlyRight.isEmpty() && edgesOnlyLeft.isEmpty() && edgesOnlyRight.isEmpty();
        }
    }

    /** A link of a neighbourhood as the page can key it again: the word, the other end's kind and name, the occurrences. */
    public record Link(String label, String kind, String name, Cardinality cardinality) {}

    private SchemaDiff() {}

    /** A declared node: not a placeholder for a built-in or an external object. */
    private static boolean declared(Node n) {
        return !NodeKind.BUILTIN.equals(n.kind()) && !NodeKind.EXTERNAL.equals(n.kind());
    }

    /** Identity of a link, cardinality included: a changed minOccurs shows as one link gone and one added. */
    private static String edgeKey(Edge e) {
        return String.join(KEY_SEPARATOR, e.from(), e.to(), e.label(), Cardinality.text(e.cardinality()));
    }

    public static Result of(SchemaGraph left, SchemaGraph right) {
        Set<String> leftIds = new HashSet<>(), rightIds = new HashSet<>();
        for (Node n : left.nodes.values()) if (declared(n)) leftIds.add(n.id());
        for (Node n : right.nodes.values()) if (declared(n)) rightIds.add(n.id());
        Map<String, Edge> leftEdges = new LinkedHashMap<>(), rightEdges = new LinkedHashMap<>();
        for (Edge e : left.edges) leftEdges.put(edgeKey(e), e);
        for (Edge e : right.edges) rightEdges.put(edgeKey(e), e);
        List<Node> nodesOnlyLeft = new ArrayList<>(), nodesOnlyRight = new ArrayList<>();
        for (Node n : left.nodes.values()) if (declared(n) && !rightIds.contains(n.id())) nodesOnlyLeft.add(n);
        for (Node n : right.nodes.values()) if (declared(n) && !leftIds.contains(n.id())) nodesOnlyRight.add(n);
        List<Edge> edgesOnlyLeft = new ArrayList<>(), edgesOnlyRight = new ArrayList<>();
        for (Map.Entry<String, Edge> e : leftEdges.entrySet()) if (!rightEdges.containsKey(e.getKey())) edgesOnlyLeft.add(e.getValue());
        for (Map.Entry<String, Edge> e : rightEdges.entrySet()) if (!leftEdges.containsKey(e.getKey())) edgesOnlyRight.add(e.getValue());
        return new Result(nodesOnlyLeft, nodesOnlyRight, edgesOnlyLeft, edgesOnlyRight);
    }

    /** A link seen from one node, told apart by what it is — its word, the other end's kind and name, its cardinality — rather than by the file it is written in. */
    public static String linkKey(Node other, Edge edge) {
        return String.join(KEY_SEPARATOR, edge.label(), other.kind(), other.name(), Cardinality.text(edge.cardinality()));
    }

    /** The links around a node of a file, both ways, by key: what its side of a comparison holds. */
    public static Map<String, Link> neighbourhood(File file, String id) {
        Map<String, Link> links = new LinkedHashMap<>();
        for (Edge e : file.out(id)) {
            Node n = file.node(e.to());
            if (n != null) links.put(linkKey(n, e), new Link(e.label(), n.kind(), n.name(), e.cardinality()));
        }
        for (Edge e : file.in(id)) {
            Node n = file.node(e.from());
            if (n != null) links.put(linkKey(n, e), new Link(e.label(), n.kind(), n.name(), e.cardinality()));
        }
        return links;
    }
}
