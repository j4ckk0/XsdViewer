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
import java.util.List;
import java.util.Set;

import org.jtools.xsdviewer.model.Library.File;
import org.jtools.xsdviewer.model.Library.Found;
import org.jtools.xsdviewer.schema.LinkLabel;
import org.jtools.xsdviewer.schema.NodeKind;
import org.jtools.xsdviewer.schema.ParticleKind;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaGraph.Attribute;
import org.jtools.xsdviewer.schema.SchemaGraph.Edge;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.jtools.xsdviewer.schema.SchemaGraph.Particle;

/**
 * The content model of a declaration as a tree of boxes, which the Model view draws and the
 * comparison of two declarations aligns: the compositors of the declaration (a box per sequence,
 * choice, all), the elements they hold with their occurrences and their type, its attributes. An
 * anonymous type is walked in place; a named type, a global element, a group or a base type is a
 * box that opens on demand, its content being that node's own, from this file or from another file
 * of the {@link Library}.
 *
 * A declaration of a WSDL or of a Schematron has no content model — no particle is written for a
 * service or a rule — but it has a chain of its own, and that chain is the model such a file has:
 * a service holds its ports, a portType its operations, an operation its messages, a message the
 * elements of its parts, where the schema's own content model takes over; a phase holds its
 * patterns, they their rules, they their assertions. Such a box is named after what the link leads
 * to, with the link's word above it, and opens the same way.
 */
public final class ContentTree {

    /** How deep "open every box" goes: enough for a schema, bounded for a recursive one. */
    public static final int EXPAND_ALL_DEPTH = 6;
    /** Between the indexes of a box's path from the root. */
    public static final String PATH_SEPARATOR = "/";
    /** Before the index of an attribute in a path: {@code /0/a1}. */
    private static final String ATTRIBUTE_MARK = "a";

    /** A node and the file it lives in; {@code n} is null when the declaration is nowhere to be found. */
    private record Place(Node n, File file) {}

    /**
     * What a box opens onto, read from {@code file}: a content model ({@code particles} and
     * {@code attributes}) or a chain ({@code links}); {@code id} is what the recursion guard watches,
     * null for content walked in place.
     */
    private record Opening(List<Particle> particles, List<Attribute> attributes, List<Edge> links, String id, File file) {}

    private final File home;
    private final Library library;
    private final Set<String> expanded;
    private final boolean openAll;

    private ContentTree(File home, Library library, Set<String> expanded, boolean openAll) {
        this.home = home;
        this.library = library;
        this.expanded = expanded;
        this.openAll = openAll;
    }

    /**
     * The tree of {@code root}, a declaration of {@code home}, which is one of the library's files.
     *
     * @param expanded the paths of the boxes the reader opened
     * @param openAll  every box open down to {@link #EXPAND_ALL_DEPTH}, whatever {@code expanded} says: what
     *                 comparing two models needs, the whole shape being compared and not what the reader unfolded
     */
    public static Box build(Node root, File home, Library library, Set<String> expanded, boolean openAll) {
        ContentTree t = new ContentTree(home, library, expanded, openAll);
        Box tree = new Box(root.kind(), root.name(), root.id(), "", true);
        Opening opening = t.openingOf(root, List.of(), home);
        if (opening != null) t.fill(tree, opening, List.of(root.id()));
        return tree;
    }

    /**
     * The node with {@code id} and the file it lives in: the file looked into, or — an external
     * placeholder — the file of the library that declares it, whose own links are then the ones to
     * follow.
     */
    private Place nodeOf(String id, File file) {
        Node n = file.node(id);
        if (n != null && !NodeKind.EXTERNAL.equals(n.kind())) return new Place(n, file);
        String name = n != null ? n.name() : SchemaGraph.nameOf(id);
        Found found = library.find(name, Library.kindsOf(id), n != null ? n.ns() : "", home);
        return found != null ? new Place(found.node(), found.file()) : new Place(n, file);
    }

    /** The content a node has of its own: its particles and attributes, or, for an element of a named type, that type's — in whichever file declares it. */
    private Opening contentOf(Node n, List<String> path, File file) {
        if (n == null) return null;
        if (!n.content().isEmpty() || !n.attributes().isEmpty()) return new Opening(n.content(), n.attributes(), null, n.id(), file);
        if (NodeKind.ELEMENT.equals(n.kind())) {   // a global element of a named type: the type's content
            for (Edge e : file.out(n.id())) {
                if (!LinkLabel.TYPE.equals(e.label())) continue;
                Place type = nodeOf(e.to(), file);
                if (type.n() != null && !path.contains(type.n().id())) return contentOf(type.n(), path, type.file());
                break;
            }
        }
        return null;
    }

    /** What a box opens onto: the content model of {@code n}, or — a WSDL's or a Schematron's own object, which has none — the links of its chain; null when there is nothing to open. */
    private Opening openingOf(Node n, List<String> ids, File file) {
        Opening content = contentOf(n, ids, file);
        if (content != null) return content;
        if (n != null && NodeKind.familyOf(n.kind()) != null) {
            List<Edge> links = file.out(n.id());
            if (!links.isEmpty()) return new Opening(null, null, links, n.id(), file);
        }
        return null;
    }

    private void fill(Box box, Opening opening, List<String> ids) {
        File file = opening.file();
        if (opening.links() != null) {   // a family object: its chain, one box per link
            int i = 0;
            for (Edge e : opening.links()) box.children.add(chainBox(e, box.path + PATH_SEPARATOR + i++, ids, file));
            return;
        }
        int i = 0;
        for (Attribute a : opening.attributes()) box.attributes.add(attributeBox(a, box.path + PATH_SEPARATOR + ATTRIBUTE_MARK + i++, file));
        i = 0;
        for (Particle p : opening.particles()) box.children.add(particleBox(p, box.path + PATH_SEPARATOR + i++, ids, file));
    }

    /** A box of a chain: what the link leads to, with the link's word above its name (a port's name, "operation", "input"...). */
    private Box chainBox(Edge edge, String path, List<String> ids, File file) {
        Place target = nodeOf(edge.to(), file);
        Box box = new Box(target.n() != null ? target.n().kind() : SchemaGraph.kindOf(edge.to()),
                target.n() != null ? target.n().name() : SchemaGraph.nameOf(edge.to()), null, path, false);
        box.ref = edge.to();
        box.word = edge.label();
        return opened(box, target, path, ids);
    }

    /** A box standing for another declaration: a handle when that one has something to open, {@code recursive} when it is already open above. */
    private Box opened(Box box, Place target, String path, List<String> ids) {
        Opening opening = target.n() != null ? openingOf(target.n(), ids, target.file()) : null;
        if (opening == null) return box;
        if (ids.contains(opening.id())) {
            box.recursive = true;
            return box;
        }
        box.expandable = true;
        box.expanded = openAll ? ids.size() <= EXPAND_ALL_DEPTH : expanded.contains(path);
        if (box.expanded) {
            List<String> deeper = new ArrayList<>(ids);
            deeper.add(opening.id());
            fill(box, opening, deeper);
        }
        return box;
    }

    private Box attributeBox(Attribute a, String path, File file) {
        Box box = new Box(NodeKind.ATTRIBUTE, a.name(), null, path, false);
        box.ref = a.ref();
        box.typeId = a.type();
        if (!a.type().isEmpty()) {
            Node type = nodeOf(a.type(), file).n();
            box.typeName = type != null ? type.name() : "";
        }
        box.card = a.use();
        return box;
    }

    private Box particleBox(Particle p, String path, List<String> ids, File file) {
        Box box = new Box(p.kind(), p.name(), null, path, false);
        box.ref = p.ref();
        box.typeId = p.type();
        box.card = p.cardinality();
        box.namespace = p.namespace();
        if (ParticleKind.isCompositor(p.kind()) || (ParticleKind.ELEMENT.equals(p.kind()) && p.ref().isEmpty() && p.type().isEmpty())) {
            // a compositor, or an element of an anonymous type: walked in place
            fill(box, new Opening(p.children(), p.attributes(), null, null, file), ids);
            return box;
        }
        // what the box refers to — a type, a global element, a group, a base type — is expanded on demand
        String targetId = !p.ref().isEmpty() ? p.ref() : p.type();
        if (targetId.isEmpty()) return box;
        Place target = nodeOf(targetId, file);
        box.typeName = target.n() != null ? target.n().name() : SchemaGraph.nameOf(targetId);
        if (!p.type().isEmpty() && target.n() != null && !NodeKind.COMPLEX_TYPE.equals(target.n().kind()) && !NodeKind.EXTERNAL.equals(target.n().kind())) {
            return box;   // a simple or built-in type: nothing inside
        }
        return opened(box, target, path, ids);
    }
}
