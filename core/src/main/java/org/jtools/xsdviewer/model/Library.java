package org.jtools.xsdviewer.model;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jtools.xsdviewer.schema.NodeKind;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaGraph.Edge;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;

/**
 * The parsed files a content model may reach: the file a declaration is read from and the other
 * files of its workspace, where a named type, a global element or a group it uses may be declared.
 * A file that references such a declaration without declaring it holds an {@code external}
 * placeholder for it; the library says which file declares it, by name, kind and namespace.
 */
public final class Library {

    /** One parsed file, its links indexed by their two ends. */
    public static final class File {
        public final String name;
        public final SchemaGraph graph;
        private final Map<String, List<Edge>> outEdges = new HashMap<>();
        private final Map<String, List<Edge>> inEdges = new HashMap<>();

        public File(String name, SchemaGraph graph) {
            this.name = name;
            this.graph = graph;
            for (Edge e : graph.edges) {
                outEdges.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
                inEdges.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e);
            }
        }

        public Node node(String id) {
            return graph.nodes.get(id);
        }

        /** The links out of the node {@code id}, in the order the file makes them. */
        public List<Edge> out(String id) {
            return outEdges.getOrDefault(id, List.of());
        }

        /** The links into the node {@code id}. */
        public List<Edge> in(String id) {
            return inEdges.getOrDefault(id, List.of());
        }
    }

    /** A declaration found: the file that declares it and its node there. */
    public record Found(File file, Node node) {}

    private final List<File> files;

    public Library(List<File> files) {
        this.files = List.copyOf(files);
    }

    public List<File> files() {
        return files;
    }

    /** The file named {@code name}, or null. */
    public File file(String name) {
        for (File f : files) if (f.name.equals(name)) return f;
        return null;
    }

    /** The kinds of declaration a placeholder id can stand for: {@code type:X} is a complex or a simple type, any other kind is itself. */
    public static Set<String> kindsOf(String id) {
        String kind = SchemaGraph.kindOf(id);
        return NodeKind.TYPE_REFERENCE.equals(kind) ? Set.of(NodeKind.COMPLEX_TYPE, NodeKind.SIMPLE_TYPE) : Set.of(kind);
    }

    /**
     * The declaration of {@code name} — one of {@code kinds}, in namespace {@code ns} — in a file other
     * than {@code skip}, or null. A schema without a target namespace (a chameleon include) declares for
     * the namespace of whoever includes it, so its declarations answer to any namespace.
     */
    public Found find(String name, Set<String> kinds, String ns, File skip) {
        for (File f : files) {
            if (f == skip) continue;
            for (String kind : kinds) {
                Node n = f.node(SchemaGraph.nodeId(kind, name));
                if (n != null && !NodeKind.EXTERNAL.equals(n.kind()) && (n.ns().equals(ns) || n.ns().isEmpty())) return new Found(f, n);
            }
        }
        return null;
    }
}
