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
import java.util.List;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.w3c.dom.Element;

/**
 * What a name in a schema stands for, and what becomes of what it names. One rule turns a qualified
 * name into a node id — a built-in type, a declaration of this file, else a reference — and the links
 * whose target is not known yet are held until every declaration has been read, which is when they are
 * closed and a placeholder node is made for whatever the file references without declaring.
 *
 * It answers {@link ContentModelBuilder.Ids} too, so that the content models name what the links name.
 * The schemas inline in a WSDL share one of these, which is why the third pass runs once for the file.
 */
final class References implements ContentModelBuilder.Ids {

    private final SchemaGraph graph;
    /** An edge whose target is not resolved yet ("type:X", "element:X", "group:X"...), with the namespace of X. */
    private record Pending(SchemaGraph.Edge edge, String ns) {}

    private final List<Pending> pending = new ArrayList<>();

    References(SchemaGraph graph) {
        this.graph = graph;
    }

    /** A qualified name split into the namespace it resolves to (empty when unbound) and its local part. */
    private record QName(String ns, String local) {
        static QName resolve(String qname, Element ctx) {
            int colon = qname.indexOf(XsdNames.QNAME_SEPARATOR);
            String prefix = colon < 0 ? null : qname.substring(0, colon);
            String local = colon < 0 ? qname : qname.substring(colon + 1);
            String ns = ctx.lookupNamespaceURI(prefix);
            return new QName(ns == null ? "" : ns, local);
        }
    }

    /** Id of the complexType or simpleType named {@code name} declared in this file, or null. */
    private String declaredType(String name) {
        String complex = SchemaGraph.nodeId(NodeKind.COMPLEX_TYPE, name);
        if (graph.declares(complex)) return complex;
        String simple = SchemaGraph.nodeId(NodeKind.SIMPLE_TYPE, name);
        return graph.declares(simple) ? simple : null;
    }


    @Override
    public String named(String kind, String qname, Element ctx) {
        return SchemaGraph.nodeId(kind, QName.resolve(qname, ctx).local());
    }

    @Override
    public String type(String qname, Element ctx) {
        return typeId(qname, ctx);
    }

    void linkType(String owner, String qname, Element ctx, String label, Cardinality card) {
        linkType(owner, qname, ctx, label, card, "");
    }

    /**
     * The node id a type name stands for, the one rule the links and the content models both follow: a
     * built-in type ({@code builtin:X}), a type declared here so far ({@code complexType:X} /
     * {@code simpleType:X}), else the reference the third pass resolves or makes a placeholder of
     * ({@code type:X}). A name in the XSD namespace is a built-in type unless this file declares it:
     * schemas that use the XSD namespace as their default namespace refer to their own types unprefixed.
     */
    private String typeId(String qname, Element ctx) {
        QName q = QName.resolve(qname, ctx);
        String declared = declaredType(q.local());
        if (declared != null) return declared;
        return XsdNames.NAMESPACE.equals(q.ns()) ? SchemaGraph.nodeId(NodeKind.BUILTIN, q.local())
                : SchemaGraph.nodeId(NodeKind.TYPE_REFERENCE, q.local());
    }

    /** A reference to a named type: a built-in is a node right away, the others are links the third pass resolves. */
    void linkType(String owner, String qname, Element ctx, String label, Cardinality card, String compositor) {
        QName q = QName.resolve(qname, ctx);
        String id = typeId(qname, ctx);
        if (NodeKind.BUILTIN.equals(SchemaGraph.kindOf(id))) {
            graph.nodes.computeIfAbsent(id, k -> new SchemaGraph.Node(id, NodeKind.BUILTIN, q.local(),
                    XsdNames.NAMESPACE, 0, Messages.get(MessageKey.BUILTIN_TYPE_DOC)));
            graph.edges.add(new SchemaGraph.Edge(owner, id, label, card, compositor));
        } else {
            pending.add(new Pending(new SchemaGraph.Edge(owner, id, label, card, compositor), q.ns()));
        }
    }

    /** A reference (ref=, substitutionGroup=, a WSDL message=, element=...) to a named declaration of a given kind. */
    void link(String owner, String kind, String qname, Element ctx, String label, Cardinality card) {
        link(owner, kind, qname, ctx, label, card, "");
    }

    void link(String owner, String kind, String qname, Element ctx, String label, Cardinality card, String compositor) {
        QName q = QName.resolve(qname, ctx);
        pending.add(new Pending(new SchemaGraph.Edge(owner, SchemaGraph.nodeId(kind, q.local()), label, card, compositor), q.ns()));
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
            graph.edges.add(new SchemaGraph.Edge(e.from(), to, e.label(), e.cardinality(), e.compositor()));
        }
        pending.clear();
        // the content models name what the links name: a type declared by another schema of the file
        // (the schemas inline in a WSDL) is only known now, as it is for the links above
        graph.nodes.replaceAll((id, n) -> n.content().isEmpty() && n.attributes().isEmpty() ? n
                : n.withContent(resolveParticles(n.content()), resolveAttributes(n.attributes())));
    }

    /** The declared type a {@code type:X} of a content model stands for, once every declaration is known; every other id stands for itself. */
    private String resolveContentType(String id) {
        if (id.isEmpty() || !NodeKind.TYPE_REFERENCE.equals(SchemaGraph.kindOf(id))) return id;
        String declared = declaredType(SchemaGraph.nameOf(id));
        return declared != null ? declared : id;
    }

    private List<SchemaGraph.Particle> resolveParticles(List<SchemaGraph.Particle> particles) {
        return particles.stream()
                .map(p -> p.resolved(resolveContentType(p.type()), resolveParticles(p.children()), resolveAttributes(p.attributes())))
                .toList();
    }

    private List<SchemaGraph.Attribute> resolveAttributes(List<SchemaGraph.Attribute> attributes) {
        return attributes.stream().map(a -> a.resolved(resolveContentType(a.type()))).toList();
    }
}
