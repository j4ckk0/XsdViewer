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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Turns the text of an XSD into a {@link SchemaGraph}: one node per global declaration,
 * one edge per direct reference from a declaration to another one.
 *
 * <p>Only the given file is read; objects it references but does not declare
 * (imported / included ones) become {@link NodeKind#EXTERNAL} nodes, XSD built-in types
 * become {@link NodeKind#BUILTIN} nodes.
 */
public final class XsdParser {

    /** Documentation longer than this is cut (the details panel shows it whole, the graph a tooltip). */
    private static final int MAX_DOCUMENTATION_LENGTH = 1000;
    private static final String WHITESPACE = "\\s+";
    private static final String LINE_BREAK_WITH_INDENT = "[ \\t]*\\n[ \\t]*";

    private final SchemaGraph graph = new SchemaGraph();
    /** An edge whose target is not resolved yet ("type:X", "element:X", "group:X"...), with the namespace of X. */
    private record Pending(SchemaGraph.Edge edge, String ns) {}

    private final List<Pending> pending = new ArrayList<>();
    private Map<String, Integer> lines = Map.of();

    private XsdParser() {}

    public static SchemaGraph parse(String xsdText) throws Exception {
        return new XsdParser().doParse(xsdText);
    }

    private SchemaGraph doParse(String text) throws Exception {
        lines = DeclarationLineIndex.build(text);
        Document doc = SecureXmlFactories.newDocumentBuilder().parse(new InputSource(new StringReader(text)));

        Element schema = doc.getDocumentElement();
        if (!XsdVocabulary.NAMESPACE.equals(schema.getNamespaceURI()) || !XsdVocabulary.SCHEMA.equals(schema.getLocalName())) {
            throw new IllegalArgumentException(Messages.get(MessageKey.NOT_A_SCHEMA, schema.getTagName()));
        }
        graph.targetNamespace = schema.getAttribute(XsdVocabulary.ATTR_TARGET_NAMESPACE);

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
                graph.nodes.put(id, new SchemaGraph.Node(id, ln, name, graph.targetNamespace,
                        lines.getOrDefault(id, 0), documentation(c)));
            }
        }

        // Pass 2: the links.
        for (Element c : children(schema)) {
            if (isGlobalDeclaration(c)) {
                collect(c, SchemaGraph.nodeId(c.getLocalName(), c.getAttribute(XsdVocabulary.ATTR_NAME)), true);
            }
        }

        // Pass 3: resolve targets, creating placeholder nodes for what this file does not declare.
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
            graph.edges.add(new SchemaGraph.Edge(e.from(), to, e.label()));
        }
        return graph;
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
     * @param e     the element being examined
     * @param owner id of the global declaration all links are attributed to
     * @param self  true when {@code e} is the global declaration itself (its own type /
     *              substitutionGroup are then labelled differently from a nested element's)
     */
    private void collect(Element e, String owner, boolean self) {
        if (!XsdVocabulary.NAMESPACE.equals(e.getNamespaceURI())) return; // e.g. content of xs:appinfo
        String ln = e.getLocalName();
        switch (ln) {
            case XsdVocabulary.ANNOTATION -> { return; }
            case XsdVocabulary.ELEMENT -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_REF)) {
                    link(owner, NodeKind.ELEMENT, e.getAttribute(XsdVocabulary.ATTR_REF), e, LinkLabel.REF);
                    return;
                }
                String name = e.getAttribute(XsdVocabulary.ATTR_NAME);
                if (e.hasAttribute(XsdVocabulary.ATTR_TYPE)) {
                    // a nested element is labelled with just its name: "shipTo", not "child shipTo"
                    linkType(owner, e.getAttribute(XsdVocabulary.ATTR_TYPE), e, self ? LinkLabel.TYPE : name);
                }
                if (self && e.hasAttribute(XsdVocabulary.ATTR_SUBSTITUTION_GROUP)) {
                    link(owner, NodeKind.ELEMENT, e.getAttribute(XsdVocabulary.ATTR_SUBSTITUTION_GROUP), e, LinkLabel.SUBSTITUTES);
                }
            }
            case XsdVocabulary.ATTRIBUTE -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_REF)) {
                    link(owner, NodeKind.ATTRIBUTE, e.getAttribute(XsdVocabulary.ATTR_REF), e, LinkLabel.ATTRIBUTE_REF);
                    return;
                }
                if (e.hasAttribute(XsdVocabulary.ATTR_TYPE)) {
                    linkType(owner, e.getAttribute(XsdVocabulary.ATTR_TYPE), e,
                            self ? LinkLabel.TYPE : LinkLabel.attribute(e.getAttribute(XsdVocabulary.ATTR_NAME)));
                }
            }
            case XsdVocabulary.GROUP -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_REF)) {
                    link(owner, NodeKind.GROUP, e.getAttribute(XsdVocabulary.ATTR_REF), e, LinkLabel.GROUP);
                    return;
                }
            }
            case XsdVocabulary.ATTRIBUTE_GROUP -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_REF)) {
                    link(owner, NodeKind.ATTRIBUTE_GROUP, e.getAttribute(XsdVocabulary.ATTR_REF), e, LinkLabel.ATTRIBUTE_GROUP);
                    return;
                }
            }
            case XsdVocabulary.EXTENSION -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_BASE)) linkType(owner, e.getAttribute(XsdVocabulary.ATTR_BASE), e, LinkLabel.EXTENDS);
            }
            case XsdVocabulary.RESTRICTION -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_BASE)) linkType(owner, e.getAttribute(XsdVocabulary.ATTR_BASE), e, LinkLabel.RESTRICTS);
            }
            case XsdVocabulary.LIST -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_ITEM_TYPE)) linkType(owner, e.getAttribute(XsdVocabulary.ATTR_ITEM_TYPE), e, LinkLabel.LIST_OF);
            }
            case XsdVocabulary.UNION -> {
                if (e.hasAttribute(XsdVocabulary.ATTR_MEMBER_TYPES)) {
                    for (String t : e.getAttribute(XsdVocabulary.ATTR_MEMBER_TYPES).trim().split(WHITESPACE)) {
                        if (!t.isEmpty()) linkType(owner, t, e, LinkLabel.UNION_OF);
                    }
                }
            }
            default -> { }
        }
        for (Element c : children(e)) collect(c, owner, false);
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
    private void linkType(String owner, String qname, Element ctx, String label) {
        QName q = QName.resolve(qname, ctx);
        // A name in the XSD namespace is a built-in type, unless this file declares it: schemas
        // that use the XSD namespace as their default namespace refer to their own types unprefixed.
        if (XsdVocabulary.NAMESPACE.equals(q.ns()) && declaredType(q.local()) == null) {
            String id = SchemaGraph.nodeId(NodeKind.BUILTIN, q.local());
            graph.nodes.computeIfAbsent(id, k -> new SchemaGraph.Node(id, NodeKind.BUILTIN, q.local(),
                    XsdVocabulary.NAMESPACE, 0, Messages.get(MessageKey.BUILTIN_TYPE_DOC)));
            graph.edges.add(new SchemaGraph.Edge(owner, id, label));
        } else {
            pending.add(new Pending(new SchemaGraph.Edge(owner, SchemaGraph.nodeId(NodeKind.TYPE_REFERENCE, q.local()), label), q.ns()));
        }
    }

    /** A reference (ref=, substitutionGroup=) to a named declaration of a given kind. */
    private void link(String owner, String kind, String qname, Element ctx, String label) {
        QName q = QName.resolve(qname, ctx);
        pending.add(new Pending(new SchemaGraph.Edge(owner, SchemaGraph.nodeId(kind, q.local()), label), q.ns()));
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

    private static List<Element> children(Element e) {
        NodeList nl = e.getChildNodes();
        List<Element> out = new ArrayList<>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) out.add((Element) n);
        }
        return out;
    }
}
