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
     * A particle of a content model (the Model view): a compositor ({@code sequence}, {@code choice},
     * {@code all}) with its children, an element (its {@code name}; {@code ref}: the node id of the
     * global element it refers to, else {@code type}: the node id of its type, else its anonymous
     * type's {@code children} and {@code attributes}), a group reference ({@code ref}), a wildcard
     * ({@code namespace}: its constraint), or the base type of a derivation ({@code extends} /
     * {@code restricts}, {@code type}). {@code cardinality}: the particle's own occurrences, null for a base type.
     */
    public record Particle(String kind, String name, String ref, String type, Cardinality cardinality, String namespace,
                           List<Particle> children, List<Attribute> attributes) {

        /** An {@code xs:sequence}, {@code xs:choice} or {@code xs:all}: its occurrences and what it holds. */
        public static Particle compositor(String kind, Cardinality cardinality, List<Particle> children) {
            return new Particle(kind, "", "", "", cardinality, "", children, List.of());
        }

        /** An element declared where it is used: its name, the node id of its type (empty when it declares an anonymous one, whose content is here). */
        public static Particle element(String name, String type, Cardinality cardinality, List<Particle> children, List<Attribute> attributes) {
            return new Particle(ParticleKind.ELEMENT, name, "", type, cardinality, "", children, attributes);
        }

        /** A reference to a global element or group: the node id it names, whose content the view opens from that node. */
        public static Particle reference(String kind, String ref, Cardinality cardinality) {
            return new Particle(kind, SchemaGraph.nameOf(ref), ref, "", cardinality, "", List.of(), List.of());
        }

        /** The base type of a derivation ({@code extends} / {@code restricts}): the node id of the type derived from; it has no occurrences of its own. */
        public static Particle baseType(String kind, String type) {
            return new Particle(kind, SchemaGraph.nameOf(type), "", type, null, "", List.of(), List.of());
        }

        /** A wildcard ({@code xs:any}): the namespaces it accepts, which is also what names it. */
        public static Particle wildcard(String namespace, Cardinality cardinality) {
            return new Particle(ParticleKind.ANY, namespace, "", "", cardinality, namespace, List.of(), List.of());
        }

        /** The same particle with its type reference and its content resolved (see {@code XsdParser.resolve()}). */
        Particle resolved(String type, List<Particle> children, List<Attribute> attributes) {
            return new Particle(kind, name, ref, type, cardinality, namespace, children, attributes);
        }
    }

    /** An attribute of a content model: its {@code name}; {@code ref}: the global attribute or attributeGroup it refers to; {@code type}; {@code use} (null for a group or a wildcard). */
    public record Attribute(String name, String ref, String type, Cardinality use) {

        /** An attribute declared where it is used: its name and the node id of its type (empty when it declares an anonymous one). */
        public static Attribute declared(String name, String type, Cardinality use) {
            return new Attribute(name, "", type, use);
        }

        /** A reference to a global attribute or attributeGroup: the node id it names. */
        public static Attribute reference(String ref, Cardinality use) {
            return new Attribute(SchemaGraph.nameOf(ref), ref, "", use);
        }

        /** An {@code xs:anyAttribute}: the namespaces it accepts, which is also what names it. */
        public static Attribute wildcard(String namespace) {
            return new Attribute(namespace, "", "", null);
        }

        /** The same attribute with its type reference resolved (see {@code XsdParser.resolve()}). */
        Attribute resolved(String type) {
            return new Attribute(name, ref, type, use);
        }
    }

    /** One {@code xs:enumeration} of a declaration: its value and the documentation of that value (may be empty). */
    public record Value(String value, String doc) {}

    /**
     * A global schema object: a declaration, or a placeholder ({@link NodeKind#BUILTIN}, {@link NodeKind#EXTERNAL}).
     * {@code ns}: the namespace the name lives in (for a placeholder, where the client looks for the
     * declaring file); {@code line}: 1-based, 0 when unknown; {@code values}: the enumeration the
     * declaration restricts its type to, empty when it is not an enumeration; {@code members}: the
     * names of the elements and attributes inside the declaration (a message's parts), for the search;
     * {@code xpath}: the expression a Schematron rule or assertion is made of (its context, its test), empty otherwise;
     * {@code content} and {@code attributes}: the content model of an element's anonymous type, a complexType, a group, an attributeGroup.
     */
    public record Node(String id, String kind, String name, String ns, int line, int endLine, String doc, List<Value> values, List<String> members,
                       String xpath, List<Particle> content, List<Attribute> attributes) {
        /** A node whose declaration is nowhere in this file: a built-in, or an object of another schema. */
        public Node(String id, String kind, String name, String ns, int line, String doc) {
            this(id, kind, name, ns, line, 0, doc, List.of(), List.of(), "", List.of(), List.of());
        }

        public Node(String id, String kind, String name, String ns, int line, int endLine, String doc) {
            this(id, kind, name, ns, line, endLine, doc, List.of(), List.of(), "", List.of(), List.of());
        }

        public Node(String id, String kind, String name, String ns, int line, int endLine, String doc, List<Value> values) {
            this(id, kind, name, ns, line, endLine, doc, values, List.of(), "", List.of(), List.of());
        }

        public Node withMembers(List<String> members) {
            return new Node(id, kind, name, ns, line, endLine, doc, values, members, xpath, content, attributes);
        }

        public Node withXpath(String xpath) {
            return new Node(id, kind, name, ns, line, endLine, doc, values, members, xpath, content, attributes);
        }

        public Node withContent(List<Particle> content, List<Attribute> attributes) {
            return new Node(id, kind, name, ns, line, endLine, doc, values, members, xpath, content, attributes);
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

/**
     * A direct link from one node to another ({@code label}: a {@link LinkLabel}); {@code cardinality}
     * is null for type links; {@code compositor} is the {@code xs:sequence}, {@code xs:choice} or
     * {@code xs:all} a nested element (or a group reference) sits in, empty for every other link.
     */
    public record Edge(String from, String to, String label, Cardinality cardinality, String compositor) {
        public Edge(String from, String to, String label) {
            this(from, to, label, null, "");
        }

        public Edge(String from, String to, String label, Cardinality cardinality) {
            this(from, to, label, cardinality, "");
        }
    }

    /** An xs:import / xs:include / xs:redefine found at the top of the schema, a wsdl:import, a Schematron include. */
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
