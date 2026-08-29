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

    /**
     * A global schema object: a declaration ({@link NodeKind#GLOBAL_DECLARATIONS}), or a
     * placeholder for a built-in XSD type ({@link NodeKind#BUILTIN}) / an object not declared in
     * this file ({@link NodeKind#EXTERNAL}). {@code ns} is the namespace the name lives in: the
     * target namespace for declarations, the referenced namespace for external placeholders (what
     * the client uses to find the file declaring it). {@code line} is 1-based, 0 when unknown.
     */
    public record Node(String id, String kind, String name, String ns, int line, String doc) {}

    /**
     * How many times the target of a link occurs in its owner: {@code minOccurs..maxOccurs} of a
     * nested element or group reference, adjusted by the enclosing sequence / all / choice (a
     * choice makes its branches optional) and counted from the nearest enclosing element; the
     * {@code use} of an attribute. Type links (extends, restricts, list of...) have none.
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

    /**
     * A direct link from one node to another, e.g. element -> its type; {@code label} is a
     * {@link LinkLabel}; {@code cardinality} is null when the link has none (type links).
     */
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
