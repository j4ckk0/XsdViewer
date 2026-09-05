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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.schema.DeclarationLineIndex.Span;
import org.jtools.xsdviewer.schema.DeclarationLineIndex.Tag;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Turns an XSD into a {@link SchemaGraph}: one node per global declaration, one edge per direct
 * reference. Only this file is read: what it references without declaring becomes a placeholder
 * node ({@link NodeKind#EXTERNAL}, {@link NodeKind#BUILTIN}). The passes work on any {@code xs:schema}
 * element, so that {@link WsdlParser} runs them on the schemas inline in a WSDL, into its own graph.
 * Entry point for a file: {@link SchemaParser}.
 */
final class XsdParser {

    /** Documentation longer than this is cut (the details panel shows it whole, the graph a tooltip). */
    private static final int MAX_DOCUMENTATION_LENGTH = 1000;
    private static final String WHITESPACE = "\\s+";
    private static final String LINE_BREAK_WITH_INDENT = "[ \\t]*\\n[ \\t]*";

    private final SchemaGraph graph;
    /** What a name stands for, and the links waiting for the end of the file: {@link References}. */
    private final References refs;
    private final Map<String, Span> lines;
    /** The names of the elements and attributes met inside each declaration, by owner id (for the search). */
    private final Map<String, Set<String>> members = new HashMap<>();
    /** The element declaring each xs:key / xs:unique, by the key's name: what a keyref refers to. */
    private final Map<String, String> keyOwners = new HashMap<>();
    /** The keyrefs met: (owner, keyref name, key's qualified name, the keyref element) — resolved once every key is known. */
    private record KeyRef(String owner, String name, String refer, Element ctx) {}

    private final List<KeyRef> keyRefs = new ArrayList<>();

    /** What a name of this file stands for: {@link WsdlParser} records its own references through it, and closes them once. */
    References references() {
        return refs;
    }

    /** Where the declaration of {@code id} is written, or nowhere for a node the file does not declare. */
    private Span spanOf(String id) {
        return lines.getOrDefault(id, Span.NOWHERE);
    }

    /** A parser adding to {@code graph}; {@code lines}: the lines each declaration spans, by node id (see {@link DeclarationLineIndex}). */
    XsdParser(SchemaGraph graph, Map<String, Span> lines) {
        this.graph = graph;
        this.refs = new References(graph);
        this.lines = lines;
    }

    /** The graph of an XSD file: {@code schema} is its root, {@code text} the file (for the line numbers). */
    static SchemaGraph parse(Element schema, String text) throws Exception {
        SchemaGraph graph = new SchemaGraph();
        graph.targetNamespace = schema.getAttribute(XsdNames.ATTR_TARGET_NAMESPACE);
        XsdParser parser = new XsdParser(graph, DeclarationLineIndex.build(text, XsdParser::declarationId));
        parser.collectSchema(schema);
        parser.refs.resolve();
        return graph;
    }

    /** The node declared by a tag path: a named global declaration right under the xs:schema root. */
    static String declarationId(List<Tag> path) {
        return path.size() == 2 ? globalDeclarationId(path.get(1)) : null;
    }

    /** The id of the node a tag declares when it is a named XSD global declaration (its parent being an xs:schema), else null. */
    static String globalDeclarationId(Tag t) {
        boolean declaration = XsdNames.NAMESPACE.equals(t.uri()) && NodeKind.GLOBAL_DECLARATIONS.contains(t.localName());
        return declaration && t.name() != null ? SchemaGraph.nodeId(t.localName(), t.name()) : null;
    }

    /**
     * Passes 1 and 2 on one xs:schema element: its imports, its global declarations as nodes (in the
     * schema's own target namespace), their references as links to resolve. Call {@link #resolve()} once at the end.
     */
    void collectSchema(Element schema) {
        String targetNamespace = schema.getAttribute(XsdNames.ATTR_TARGET_NAMESPACE);

        // Pass 1: the global declarations become nodes.
        for (Element c : children(schema)) {
            String ln = c.getLocalName();
            if (XsdNames.IMPORT.equals(ln) || XsdNames.INCLUDE.equals(ln) || XsdNames.REDEFINE.equals(ln)) {
                graph.imports.add(new SchemaGraph.Import(ln,
                        c.getAttribute(XsdNames.ATTR_NAMESPACE), c.getAttribute(XsdNames.ATTR_SCHEMA_LOCATION)));
                continue;
            }
            if (isGlobalDeclaration(c)) {
                String name = c.getAttribute(XsdNames.ATTR_NAME);
                String id = SchemaGraph.nodeId(ln, name);
                Span span = spanOf(id);
                graph.nodes.put(id, new SchemaGraph.Node(id, ln, name, targetNamespace,
                        span.start(), span.end(), documentation(c), enumeration(c)));
            }
        }

        // Pass 2: the links, the members met on the way, and the content model (the ids being those of the links).
        for (Element c : children(schema)) {
            if (isGlobalDeclaration(c)) {
                String id = SchemaGraph.nodeId(c.getLocalName(), c.getAttribute(XsdNames.ATTR_NAME));
                collect(c, id, true, Cardinality.ONE, "");
                Set<String> found = members.get(id);
                if (found != null) graph.nodes.computeIfPresent(id, (k, n) -> n.withMembers(List.copyOf(found)));
                ContentModelBuilder.Content content = ContentModelBuilder.of(c, refs);
                if (!content.particles().isEmpty() || !content.attributes().isEmpty()) {
                    graph.nodes.computeIfPresent(id, (k, n) -> n.withContent(content.particles(), content.attributes()));
                }
            }
        }
        // A keyref links to the element declaring the key it refers to (a key of this schema; else the keyref's owner, being what is known).
        for (KeyRef kr : keyRefs) {
            String local = kr.refer().substring(kr.refer().indexOf(XsdNames.QNAME_SEPARATOR) + 1);
            String keyOwner = keyOwners.get(local);
            if (keyOwner != null) graph.edges.add(new SchemaGraph.Edge(kr.owner(), keyOwner, LinkLabel.keyref(kr.name())));
        }
        keyRefs.clear();
    }

    /** Records a name met inside {@code owner}'s declaration: a nested element or attribute, by name or by the local name of its ref. */
    private void member(String owner, String name) {
        int colon = name.indexOf(XsdNames.QNAME_SEPARATOR);
        boolean wildcard = name.indexOf('(') >= 0;   // "any (##other)": the constraint may hold a colon
        members.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(colon < 0 || wildcard ? name : name.substring(colon + 1));
    }

    private static boolean isGlobalDeclaration(Element c) {
        return NodeKind.GLOBAL_DECLARATIONS.contains(c.getLocalName()) && c.hasAttribute(XsdNames.ATTR_NAME);
    }

    /**
     * Walks a declaration and records every reference it makes.
     *
     * @param owner     id of the global declaration the links are attributed to
     * @param self      {@code e} is the global declaration itself (its own type link is labelled differently)
     * @param enclosing occurrences of the enclosing particles since the nearest element: multiplies a nested element's own
     * @param compositor the xs:sequence / xs:choice / xs:all {@code e} sits in directly, empty when it sits in none
     */
    private void collect(Element e, String owner, boolean self, Cardinality enclosing, String compositor) {
        if (!XsdNames.NAMESPACE.equals(e.getNamespaceURI())) return; // e.g. content of xs:appinfo
        String ln = e.getLocalName();
        Cardinality inner = enclosing;   // what the children of e are enclosed in
        String innerCompositor = compositor;
        switch (ln) {
            case XsdNames.ANNOTATION -> { return; }
            case XsdNames.ELEMENT -> {
                Cardinality card = self ? null : particle(e).within(enclosing);
                if (!self && e.hasAttribute(XsdNames.ATTR_REF)) member(owner, e.getAttribute(XsdNames.ATTR_REF));
                if (!self && e.hasAttribute(XsdNames.ATTR_NAME)) member(owner, e.getAttribute(XsdNames.ATTR_NAME));
                if (e.hasAttribute(XsdNames.ATTR_REF)) {
                    refs.link(owner, NodeKind.ELEMENT, e.getAttribute(XsdNames.ATTR_REF), e, LinkLabel.REF, card, compositor);
                    return;
                }
                String name = e.getAttribute(XsdNames.ATTR_NAME);
                if (e.hasAttribute(XsdNames.ATTR_TYPE)) {
                    // a nested element is labelled with just its name: "shipTo", not "child shipTo"
                    refs.linkType(owner, e.getAttribute(XsdNames.ATTR_TYPE), e, self ? LinkLabel.TYPE : name, card, compositor);
                }
                if (self && e.hasAttribute(XsdNames.ATTR_SUBSTITUTION_GROUP)) {
                    refs.link(owner, NodeKind.ELEMENT, e.getAttribute(XsdNames.ATTR_SUBSTITUTION_GROUP), e, LinkLabel.SUBSTITUTES, null);
                }
                inner = Cardinality.ONE;   // an anonymous type's content is counted from this element
                innerCompositor = "";      // and sits in the compositors of that type, not in this element's
            }
            case XsdNames.ATTRIBUTE -> {
                Cardinality card = self ? null : attributeUse(e);
                if (!self && e.hasAttribute(XsdNames.ATTR_REF)) member(owner, e.getAttribute(XsdNames.ATTR_REF));
                if (!self && e.hasAttribute(XsdNames.ATTR_NAME)) member(owner, e.getAttribute(XsdNames.ATTR_NAME));
                if (e.hasAttribute(XsdNames.ATTR_REF)) {
                    refs.link(owner, NodeKind.ATTRIBUTE, e.getAttribute(XsdNames.ATTR_REF), e, LinkLabel.ATTRIBUTE_REF, card);
                    return;
                }
                if (e.hasAttribute(XsdNames.ATTR_TYPE)) {
                    refs.linkType(owner, e.getAttribute(XsdNames.ATTR_TYPE), e,
                            self ? LinkLabel.TYPE : LinkLabel.attribute(e.getAttribute(XsdNames.ATTR_NAME)), card);
                }
            }
            case XsdNames.GROUP -> {
                if (e.hasAttribute(XsdNames.ATTR_REF)) {
                    refs.link(owner, NodeKind.GROUP, e.getAttribute(XsdNames.ATTR_REF), e, LinkLabel.GROUP, particle(e).within(enclosing), compositor);
                    return;
                }
            }
            case XsdNames.ATTRIBUTE_GROUP -> {
                if (e.hasAttribute(XsdNames.ATTR_REF)) {
                    refs.link(owner, NodeKind.ATTRIBUTE_GROUP, e.getAttribute(XsdNames.ATTR_REF), e, LinkLabel.ATTRIBUTE_GROUP, null);
                    return;
                }
            }
            case XsdNames.ANY, XsdNames.ANY_ATTRIBUTE -> {   // a wildcard: listed among the members with its namespace constraint
                String ns = e.hasAttribute(XsdNames.ATTR_NAMESPACE) ? e.getAttribute(XsdNames.ATTR_NAMESPACE) : XsdNames.NAMESPACE_ANY;
                member(owner, ln + " (" + ns + ")");
            }
            case XsdNames.KEY, XsdNames.UNIQUE -> {
                if (e.hasAttribute(XsdNames.ATTR_NAME)) keyOwners.putIfAbsent(e.getAttribute(XsdNames.ATTR_NAME), owner);
            }
            case XsdNames.KEYREF -> {
                if (e.hasAttribute(XsdNames.ATTR_REFER)) {
                    keyRefs.add(new KeyRef(owner, e.getAttribute(XsdNames.ATTR_NAME), e.getAttribute(XsdNames.ATTR_REFER), e));
                }
            }
            case XsdNames.SEQUENCE, XsdNames.ALL -> { inner = particle(e).within(enclosing); innerCompositor = ln; }
            case XsdNames.CHOICE -> {   // one branch or another: each is optional
                inner = particle(e).within(enclosing).withMin(0);
                innerCompositor = ln;
            }
            case XsdNames.EXTENSION -> {
                if (e.hasAttribute(XsdNames.ATTR_BASE)) refs.linkType(owner, e.getAttribute(XsdNames.ATTR_BASE), e, LinkLabel.EXTENDS, null);
            }
            case XsdNames.RESTRICTION -> {
                if (e.hasAttribute(XsdNames.ATTR_BASE)) refs.linkType(owner, e.getAttribute(XsdNames.ATTR_BASE), e, LinkLabel.RESTRICTS, null);
            }
            case XsdNames.LIST -> {
                if (e.hasAttribute(XsdNames.ATTR_ITEM_TYPE)) refs.linkType(owner, e.getAttribute(XsdNames.ATTR_ITEM_TYPE), e, LinkLabel.LIST_OF, null);
            }
            case XsdNames.UNION -> {
                if (e.hasAttribute(XsdNames.ATTR_MEMBER_TYPES)) {
                    for (String t : e.getAttribute(XsdNames.ATTR_MEMBER_TYPES).trim().split(WHITESPACE)) {
                        if (!t.isEmpty()) refs.linkType(owner, t, e, LinkLabel.UNION_OF, null);
                    }
                }
            }
            default -> { }
        }
        for (Element c : children(e)) collect(c, owner, false, inner, innerCompositor);
    }

    /** minOccurs..maxOccurs of a particle (element, group reference, compositor); 1..1 when absent. */
    static Cardinality particle(Element e) {
        int min = intAttribute(e, XsdNames.ATTR_MIN_OCCURS, 1);
        String max = e.getAttribute(XsdNames.ATTR_MAX_OCCURS);
        if (XsdNames.MAX_OCCURS_UNBOUNDED.equals(max)) return new Cardinality(min, Cardinality.UNBOUNDED);
        return new Cardinality(min, intAttribute(e, XsdNames.ATTR_MAX_OCCURS, 1));
    }

    /** The cardinality of an attribute from its {@code use}: optional unless said otherwise. */
    static Cardinality attributeUse(Element e) {
        return switch (e.getAttribute(XsdNames.ATTR_USE)) {
            case XsdNames.USE_REQUIRED -> Cardinality.ONE;
            case XsdNames.USE_PROHIBITED -> Cardinality.NONE;
            default -> Cardinality.OPTIONAL;
        };
    }

    private static int intAttribute(Element e, String name, int fallback) {
        String v = e.getAttribute(name);
        if (v.isEmpty()) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            return fallback;   // a lenient viewer: a malformed value is not worth refusing the file
        }
    }

    /**
     * The values a declaration enumerates: the {@code xs:enumeration}s of its restriction — that of a
     * simpleType, of the anonymous simpleType of an element or attribute, of the simpleContent of a
     * complexType. Nothing deeper: the enumerations of nested elements are theirs.
     */
    private static List<SchemaGraph.Value> enumeration(Element decl) {
        Element restriction = switch (decl.getLocalName()) {
            case XsdNames.SIMPLE_TYPE -> child(decl, XsdNames.RESTRICTION);
            case XsdNames.ELEMENT, XsdNames.ATTRIBUTE -> {
                Element simple = child(decl, XsdNames.SIMPLE_TYPE);
                yield simple == null ? null : child(simple, XsdNames.RESTRICTION);
            }
            case XsdNames.COMPLEX_TYPE -> {
                Element content = child(decl, XsdNames.SIMPLE_CONTENT);
                yield content == null ? null : child(content, XsdNames.RESTRICTION);
            }
            default -> null;
        };
        if (restriction == null) return List.of();
        List<SchemaGraph.Value> values = new ArrayList<>();
        for (Element e : children(restriction)) {
            if (XsdNames.NAMESPACE.equals(e.getNamespaceURI()) && XsdNames.ENUMERATION.equals(e.getLocalName())) {
                values.add(new SchemaGraph.Value(e.getAttribute(XsdNames.ATTR_VALUE), documentation(e)));
            }
        }
        return values;
    }

    /** The first child element of {@code e} in the XSD namespace named {@code localName}, or null. */
    static Element child(Element e, String localName) {
        for (Element c : children(e)) {
            if (XsdNames.NAMESPACE.equals(c.getNamespaceURI()) && localName.equals(c.getLocalName())) return c;
        }
        return null;
    }

    /** The xs:documentation texts of the declaration's first xs:annotation, joined by line breaks. */
    private static String documentation(Element decl) {
        for (Element a : children(decl)) {
            if (!XsdNames.ANNOTATION.equals(a.getLocalName())) continue;
            StringBuilder sb = new StringBuilder();
            for (Element d : children(a)) {
                if (XsdNames.DOCUMENTATION.equals(d.getLocalName())) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(d.getTextContent().trim().replaceAll(LINE_BREAK_WITH_INDENT, "\n"));
                }
            }
            if (sb.length() > MAX_DOCUMENTATION_LENGTH) sb.setLength(MAX_DOCUMENTATION_LENGTH);
            return sb.toString();
        }
        return "";
    }

    static List<Element> children(Element e) {
        NodeList nl = e.getChildNodes();
        List<Element> out = new ArrayList<>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) out.add((Element) n);
        }
        return out;
    }
}
