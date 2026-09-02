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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The graph extracted from one XSD: the global schema objects (nodes) and the direct
 * ("level 1") links between them (edges). Node ids are {@code kind:name} (see {@link #nodeId}).
 */
public final class SchemaGraph {

    /** Separator between kind and name in a node id: {@code element:purchaseOrder}. */
    public static final char ID_SEPARATOR = ':';

    /** One {@code xs:enumeration} of a declaration: its value and the documentation of that value (may be empty). */
    public record Value(String value, String doc) {}

    /**
     * A global schema object: a declaration, or a placeholder ({@link NodeKind#BUILTIN}, {@link NodeKind#EXTERNAL}).
     * {@code ns}: the namespace the name lives in (for a placeholder, where the client looks for the
     * declaring file); {@code line}: 1-based, 0 when unknown; {@code values}: the enumeration the
     * declaration restricts its type to, empty when it is not an enumeration.
     */
    public record Node(String id, String kind, String name, String ns, int line, String doc, List<Value> values) {
        public Node(String id, String kind, String name, String ns, int line, String doc) {
            this(id, kind, name, ns, line, doc, List.of());
        }
    }

    /**
     * How many times a link's target occurs in its owner: minOccurs..maxOccurs through the enclosing
     * sequence / all / choice (a choice makes its branches optional), counted from the nearest
     * enclosing element; an attribute's {@code use}. Type links have none.
     */
    public record Cardinality(int min, int max) {
        /** {@code max} of an unbounded occurrence. */
        public static final int UNBOUNDED = -1;
        public static final Cardinality ONE = new Cardinality(1, 1);
        public static final Cardinality OPTIONAL = new Cardinality(0, 1);
        /** A prohibited attribute. */
        public static final Cardinality NONE = new Cardinality(0, 0);

        public boolean optional() {
            return min == 0;
        }

        /** This cardinality inside each of {@code outer} occurrences of the enclosing particle. */
        public Cardinality within(Cardinality outer) {
            int mx = max == UNBOUNDED || outer.max == UNBOUNDED ? UNBOUNDED : max * outer.max;
            return new Cardinality(min * outer.min, mx);
        }

        public Cardinality withMin(int newMin) {
            return new Cardinality(newMin, max);
        }
    }

    /** A direct link from one node to another ({@code label}: a {@link LinkLabel}); {@code cardinality} is null for type links. */
    public record Edge(String from, String to, String label, Cardinality cardinality) {
        public Edge(String from, String to, String label) {
            this(from, to, label, null);
        }
    }

    /** An xs:import / xs:include / xs:redefine found at the top of the schema. */
    public record Import(String tag, String namespace, String schemaLocation) {}

    public String targetNamespace = "";
    public final List<Import> imports = new ArrayList<>();
    /** By id, in declaration order. */
    public final Map<String, Node> nodes = new LinkedHashMap<>();
    /** In discovery order; identical parallel edges collapse. */
    public final LinkedHashSet<Edge> edges = new LinkedHashSet<>();

    public static String nodeId(String kind, String name) {
        return kind + ID_SEPARATOR + name;
    }

    public static String kindOf(String id) {
        return id.substring(0, id.indexOf(ID_SEPARATOR));
    }

    public static String nameOf(String id) {
        return id.substring(id.indexOf(ID_SEPARATOR) + 1);
    }

    public boolean declares(String id) {
        return nodes.containsKey(id);
    }

    public String toJson() {
        return SchemaGraphJsonWriter.write(this);
    }
}
