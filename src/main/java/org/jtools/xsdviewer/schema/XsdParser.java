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
    /** An edge whose target is not resolved yet ("type:X", "element:X", "group:X"...), with the namespace of X. */
    private record Pending(SchemaGraph.Edge edge, String ns) {}

    private final List<Pending> pending = new ArrayList<>();
    private final Map<String, Integer> lines;
    /** The names of the elements and attributes met inside each declaration, by owner id (for the search). */
    private final Map<String, Set<String>> members = new HashMap<>();
    /** The element declaring each xs:key / xs:unique, by the key's name: what a keyref refers to. */
    private final Map<String, String> keyOwners = new HashMap<>();
    /** The keyrefs met: (owner, keyref name, key's qualified name, the keyref element) — resolved once every key is known. */
    private record KeyRef(String owner, String name, String refer, Element ctx) {}

    private final List<KeyRef> keyRefs = new ArrayList<>();

    /** A parser adding to {@code graph}; {@code lines}: the line of each declaration by node id (see {@link DeclarationLineIndex}). */
    XsdParser(SchemaGraph graph, Map<String, Integer> lines) {
        this.graph = graph;
        this.lines = lines;
    }

    /** The graph of an XSD file: {@code schema} is its root, {@code text} the file (for the line numbers). */
    static SchemaGraph parse(Element schema, String text) throws Exception {
        SchemaGraph graph = new SchemaGraph();
        graph.targetNamespace = schema.getAttribute(XsdVocabulary.ATTR_TARGET_NAMESPACE);
        XsdParser parser = new XsdParser(graph, DeclarationLineIndex.build(text, XsdParser::declarationId));
        parser.collectSchema(schema);
        parser.resolve();
        return graph;
    }

    /** The node declared by a tag path: a named global declaration right under the xs:schema root. */
    static String declarationId(List<Tag> path) {
        return path.size() == 2 ? globalDeclarationId(path.get(1)) : null;
    }

    /** The id of the node a tag declares when it is a named XSD global declaration (its parent being an xs:schema), else null. */
    static String globalDeclarationId(Tag t) {
        boolean declaration = XsdVocabulary.NAMESPACE.equals(t.uri()) && NodeKind.GLOBAL_DECLARATIONS.contains(t.localName());
        return declaration && t.name() != null ? SchemaGraph.nodeId(t.localName(), t.name()) : null;
    }

    /**
     * Passes 1 and 2 on one xs:schema element: its imports, its global declarations as nodes (in the
     * schema's own target namespace), their references as links to resolve. Call {@link #resolve()} once at the end.
     */
    void collectSchema(Element schema) {
        String targetNamespace = schema.getAttribute(XsdVocabulary.ATTR_TARGET_NAMESPACE);

        // Pass 1: the global declarations become nodes.
        for (Element c : children(schema)) {
            String ln = c.getLocalName();
            if (XsdVocabulary.IMPORT.equals(ln) || XsdVocabulary.INCLUDE.equals(ln) || XsdVocabulary.REDEFINE.equals(ln)) {
                graph.imports.add(new SchemaGraph.Import(ln,
                        c.getAttribute(XsdVocabulary.ATTR_NAMESPACE), c.getAttribute(XsdVocabulary.ATTR_SCHEMA_LOCATION)));
                continue;
            }
            if (isGlobalDeclaration(c)) {
                String name = c.getAttribute(XsdVocabulary.ATTR_NAME);
                String id = SchemaGraph.nodeId(ln, name);
                graph.nodes.put(id, new SchemaGraph.Node(id, ln, name, targetNamespace,
                        lines.getOrDefault(id, 0), documentation(c), enumeration(c)));
            }
        }

        // Pass 2: the links, and the members met on the way.
        for (Element c : children(schema)) {
            if (isGlobalDeclaration(c)) {
                String id = SchemaGraph.nodeId(c.getLocalName(), c.getAttribute(XsdVocabulary.ATTR_NAME));
                collect(c, id, true, Cardinality.ONE);
                Set<String> found = members.get(id);
                if (found != null) graph.nodes.computeIfPresent(id, (k, n) -> n.withMembers(List.copyOf(found)));
            }
        }
        // A keyref links to the element declaring the key it refers to (a key of this schema; else the keyref's owner, being what is known).
        for (KeyRef kr : keyRefs) {
            String local = kr.refer().substring(kr.refer().indexOf(XsdVocabulary.QNAME_SEPARATOR) + 1);
            String keyOwner = keyOwners.get(local);
            if (keyOwner != null) graph.edges.add(new SchemaGraph.Edge(kr.owner(), keyOwner, LinkLabel.keyref(kr.name())));
        }
        keyRefs.clear();
    }

    /** Records a name met inside {@code owner}'s declaration: a nested element or attribute, by name or by the local name of its ref. */
    private void member(String owner, String name) {
        int colon = name.indexOf(XsdVocabulary.QNAME_SEPARATOR);
        boolean wildcard = name.indexOf('(') >= 0;   // "any (##other)": the constraint may hold a colon
        members.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(colon < 0 || wildcard ? name : name.substring(colon + 1));
    }

    /** Pass 3: resolves the targets of the links, creating placeholder nodes for what the file does not declare. */
    void resolve() {
        for (Pending p : pending) {
            SchemaGraph.Edge e = p.edge();
            String to = e.to();
            if (!graph.declares(to)) {
                String kind = SchemaGraph.kindOf(to);
                String name = SchemaGraph.nameOf(to);
                if (NodeKind.TYPE_REFERENCE.equals(kind)) {
                    String declared = declaredType(name);
                    if (declared != null) to = declared;
                }
                if (!graph.declares(to)) {
                    graph.nodes.put(to, new SchemaGraph.Node(to, NodeKind.EXTERNAL, name, p.ns(), 0,
                            Messages.get(MessageKey.EXTERNAL_DECLARATION_DOC, kind)));
                }
            }
            graph.edges.add(new SchemaGraph.Edge(e.from(), to, e.label(), e.cardinality()));
        }
        pending.clear();
    }

    private static boolean isGlobalDeclaration(Element c) {
        return NodeKind.GLOBAL_DECLARATIONS.contains(c.getLocalName()) && c.hasAttribute(XsdVocabulary.ATTR_NAME);
    }

    /** Id of the complexType or simpleType named {@code name} declared in this file, or null. */
    private String declaredType(String name) {
        String complex = SchemaGraph.nodeId(NodeKind.COMPLEX_TYPE, name);
        if (graph.declares(complex)) return complex;
        String simple = SchemaGraph.nodeId(NodeKind.SIMPLE_TYPE, name);
        return graph.declares(simple) ? simple : null;
    }

    /**
     * Walks a declaration and records every reference it makes.
     *
     * @param owner     id of the global declaration the links are attributed to
     * @param self      {@code e} is the global declaration itself (its own type link is labelled differently)
     * @param enclosing occurrences of the enclosing particles since the nearest element: multiplies a nested element's own
     */
    private void collect(Element e, String owner, boolean self, Cardinality enclosing) {
        if (!XsdVocabulary.NAMESPACE.equals(e.getNamespaceURI())) return; // e.g. content of xs:appinfo
        String ln = e.getLocalName();
        Cardinality inner = enclosing;   // what the children of e are enclosed in
        switch (ln) {
            case XsdVocabulary.ANNOTATION -> { return; }
            case XsdVocabulary.ELEMENT -> {
                Cardinality card = self ? null : particle(e).within(enclosing);
                if (!self && e.hasAttribute(XsdVocabulary.ATTR_REF)) member(owner, e.getAttribute(XsdVocabulary.ATTR_REF));
                if (!self && e.hasAttribute(XsdVocabulary.ATTR_NAME)) member(owner, e.getAttribute(XsdVocabulary.ATTR_NAME));
                if (e.hasAttribute(XsdVocabulary.ATTR_REF)) {
                    link(owner, NodeKind.ELEMENT, e.getAttribute(XsdVocabulary.ATTR_REF), e, LinkLabel.REF, card);
                    return;
                }
                String name = e.getAttribute(XsdVocabulary.ATTR_NAME);
                if (e.hasAttribute(XsdVocabulary.ATTR_TYPE)) {
                    // a nested element is labelled with just its name: "shipTo", not "child shipTo"
                    linkType(owner, e.getAttribute(XsdVocabulary.ATTR_TYPE), e, self ? LinkLabel.TYPE : name, card);
                }
                if (self && e.hasAttribute(XsdVocabulary.ATTR_SUBSTITUTION_GROUP)) {
                    link(owner, NodeKind.ELEMENT, e.getAttribute(XsdVocabulary.ATTR_SUBSTITUTION_GROUP), e, LinkLabel.SUBSTITUTES, null);
                }
                inner = Cardinality.ONE;   // an anonymous type's content is counted from this element
            }
            case XsdVocabulary.ATTRIBUTE -> {
                Cardinality card = self ? null : attributeUse(e);
                if (!self && e.hasAttribute(XsdVocabulary.ATTR_REF)) member(owner, e.getAttribute(XsdVocabulary.ATTR_REF));
                if (!self && e.hasAttribute(XsdVocabulary.ATTR_NAME)) member(owner, e.getAttribute(XsdVocabulary.ATTR_NAME));
                if (e.hasAttribute(XsdVocabulary.ATTR_REF)) {
                    link(owner, NodeKind.ATTRIBUTE, e.getAttribute(XsdVocabulary.ATTR_REF), e, LinkLabel.ATTRIBUTE_REF, card);
                    return;
                }
                if (e.hasAttribute(XsdVocabulary.ATTR_TYPE)) {
                    linkType(owner, e.getAttribute(XsdVocabulary.ATTR_TYPE), e,
                            self ? LinkLabel.TYPE : LinkLabel.attribute(e.getAttribute(XsdVocabulary.ATTR_NAME)), card);
                }
            }
            case XsdVocabulary.GROUP -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_REF)) {
                    link(owner, NodeKind.GROUP, e.getAttribute(XsdVocabulary.ATTR_REF), e, LinkLabel.GROUP, particle(e).within(enclosing));
                    return;
                }
            }
            case XsdVocabulary.ATTRIBUTE_GROUP -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_REF)) {
                    link(owner, NodeKind.ATTRIBUTE_GROUP, e.getAttribute(XsdVocabulary.ATTR_REF), e, LinkLabel.ATTRIBUTE_GROUP, null);
                    return;
                }
            }
            case XsdVocabulary.ANY, XsdVocabulary.ANY_ATTRIBUTE -> {   // a wildcard: listed among the members with its namespace constraint
                String ns = e.hasAttribute(XsdVocabulary.ATTR_NAMESPACE) ? e.getAttribute(XsdVocabulary.ATTR_NAMESPACE) : XsdVocabulary.NAMESPACE_ANY;
                member(owner, ln + " (" + ns + ")");
            }
            case XsdVocabulary.KEY, XsdVocabulary.UNIQUE -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_NAME)) keyOwners.putIfAbsent(e.getAttribute(XsdVocabulary.ATTR_NAME), owner);
            }
            case XsdVocabulary.KEYREF -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_REFER)) {
                    keyRefs.add(new KeyRef(owner, e.getAttribute(XsdVocabulary.ATTR_NAME), e.getAttribute(XsdVocabulary.ATTR_REFER), e));
                }
            }
            case XsdVocabulary.SEQUENCE, XsdVocabulary.ALL -> inner = particle(e).within(enclosing);
            case XsdVocabulary.CHOICE -> inner = particle(e).within(enclosing).withMin(0);   // one branch or another: each is optional
            case XsdVocabulary.EXTENSION -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_BASE)) linkType(owner, e.getAttribute(XsdVocabulary.ATTR_BASE), e, LinkLabel.EXTENDS, null);
            }
            case XsdVocabulary.RESTRICTION -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_BASE)) linkType(owner, e.getAttribute(XsdVocabulary.ATTR_BASE), e, LinkLabel.RESTRICTS, null);
            }
            case XsdVocabulary.LIST -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_ITEM_TYPE)) linkType(owner, e.getAttribute(XsdVocabulary.ATTR_ITEM_TYPE), e, LinkLabel.LIST_OF, null);
            }
            case XsdVocabulary.UNION -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_MEMBER_TYPES)) {
                    for (String t : e.getAttribute(XsdVocabulary.ATTR_MEMBER_TYPES).trim().split(WHITESPACE)) {
                        if (!t.isEmpty()) linkType(owner, t, e, LinkLabel.UNION_OF, null);
                    }
                }
            }
            default -> { }
        }
        for (Element c : children(e)) collect(c, owner, false, inner);
    }

    /** minOccurs..maxOccurs of a particle (element, group reference, compositor); 1..1 when absent. */
    private static Cardinality particle(Element e) {
        int min = intAttribute(e, XsdVocabulary.ATTR_MIN_OCCURS, 1);
        String max = e.getAttribute(XsdVocabulary.ATTR_MAX_OCCURS);
        if (XsdVocabulary.MAX_OCCURS_UNBOUNDED.equals(max)) return new Cardinality(min, Cardinality.UNBOUNDED);
        return new Cardinality(min, intAttribute(e, XsdVocabulary.ATTR_MAX_OCCURS, 1));
    }

    /** The cardinality of an attribute from its {@code use}: optional unless said otherwise. */
    private static Cardinality attributeUse(Element e) {
        return switch (e.getAttribute(XsdVocabulary.ATTR_USE)) {
            case XsdVocabulary.USE_REQUIRED -> Cardinality.ONE;
            case XsdVocabulary.USE_PROHIBITED -> Cardinality.NONE;
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

    /** A qualified name split into the namespace it resolves to (empty when unbound) and its local part. */
    private record QName(String ns, String local) {
        static QName resolve(String qname, Element ctx) {
            int colon = qname.indexOf(XsdVocabulary.QNAME_SEPARATOR);
            String prefix = colon < 0 ? null : qname.substring(0, colon);
            String local = colon < 0 ? qname : qname.substring(colon + 1);
            String ns = ctx.lookupNamespaceURI(prefix);
            return new QName(ns == null ? "" : ns, local);
        }
    }

    /** A reference to a named type: built-in XSD types are resolved now, the others at the end. */
    void linkType(String owner, String qname, Element ctx, String label, Cardinality card) {
        QName q = QName.resolve(qname, ctx);
        // A name in the XSD namespace is a built-in type, unless this file declares it: schemas
        // that use the XSD namespace as their default namespace refer to their own types unprefixed.
        if (XsdVocabulary.NAMESPACE.equals(q.ns()) && declaredType(q.local()) == null) {
            String id = SchemaGraph.nodeId(NodeKind.BUILTIN, q.local());
            graph.nodes.computeIfAbsent(id, k -> new SchemaGraph.Node(id, NodeKind.BUILTIN, q.local(),
                    XsdVocabulary.NAMESPACE, 0, Messages.get(MessageKey.BUILTIN_TYPE_DOC)));
            graph.edges.add(new SchemaGraph.Edge(owner, id, label, card));
        } else {
            pending.add(new Pending(new SchemaGraph.Edge(owner, SchemaGraph.nodeId(NodeKind.TYPE_REFERENCE, q.local()), label, card), q.ns()));
        }
    }

    /** A reference (ref=, substitutionGroup=, a WSDL message=, element=...) to a named declaration of a given kind. */
    void link(String owner, String kind, String qname, Element ctx, String label, Cardinality card) {
        QName q = QName.resolve(qname, ctx);
        pending.add(new Pending(new SchemaGraph.Edge(owner, SchemaGraph.nodeId(kind, q.local()), label, card), q.ns()));
    }

    /**
     * The values a declaration enumerates: the {@code xs:enumeration}s of its restriction — that of a
     * simpleType, of the anonymous simpleType of an element or attribute, of the simpleContent of a
     * complexType. Nothing deeper: the enumerations of nested elements are theirs.
     */
    private static List<SchemaGraph.Value> enumeration(Element decl) {
        Element restriction = switch (decl.getLocalName()) {
            case XsdVocabulary.SIMPLE_TYPE -> child(decl, XsdVocabulary.RESTRICTION);
            case XsdVocabulary.ELEMENT, XsdVocabulary.ATTRIBUTE -> {
                Element simple = child(decl, XsdVocabulary.SIMPLE_TYPE);
                yield simple == null ? null : child(simple, XsdVocabulary.RESTRICTION);
            }
            case XsdVocabulary.COMPLEX_TYPE -> {
                Element content = child(decl, XsdVocabulary.SIMPLE_CONTENT);
                yield content == null ? null : child(content, XsdVocabulary.RESTRICTION);
            }
            default -> null;
        };
        if (restriction == null) return List.of();
        List<SchemaGraph.Value> values = new ArrayList<>();
        for (Element e : children(restriction)) {
            if (XsdVocabulary.NAMESPACE.equals(e.getNamespaceURI()) && XsdVocabulary.ENUMERATION.equals(e.getLocalName())) {
                values.add(new SchemaGraph.Value(e.getAttribute(XsdVocabulary.ATTR_VALUE), documentation(e)));
            }
        }
        return values;
    }

    /** The first child element of {@code e} in the XSD namespace named {@code localName}, or null. */
    private static Element child(Element e, String localName) {
        for (Element c : children(e)) {
            if (XsdVocabulary.NAMESPACE.equals(c.getNamespaceURI()) && localName.equals(c.getLocalName())) return c;
        }
        return null;
    }

    /** The xs:documentation texts of the declaration's first xs:annotation, joined by line breaks. */
    private static String documentation(Element decl) {
        for (Element a : children(decl)) {
            if (!XsdVocabulary.ANNOTATION.equals(a.getLocalName())) continue;
            StringBuilder sb = new StringBuilder();
            for (Element d : children(a)) {
                if (XsdVocabulary.DOCUMENTATION.equals(d.getLocalName())) {
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
