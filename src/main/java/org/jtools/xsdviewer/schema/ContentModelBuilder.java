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

import org.jtools.xsdviewer.schema.SchemaGraph.Attribute;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.jtools.xsdviewer.schema.SchemaGraph.Particle;
import org.w3c.dom.Element;

/**
 * The content model of a global declaration, as the Model view draws it: the tree of its particles
 * (compositors with their occurrences, elements with their own occurrences and their type, group
 * references, wildcards, the base type of a derived complex type first) and its attributes. An
 * anonymous type is walked into the particle that declares it; a named type, a group, a base type
 * are named by their node id, which the view expands from the node's own content. The ids come from
 * the parser ({@link Ids}), so that they are those of the graph.
 */
final class ContentModelBuilder {

    /** What the parser knows: the node id a type name resolves to, the node id of a named declaration of a kind. */
    interface Ids {
        String type(String qname, Element ctx);

        String named(String kind, String qname, Element ctx);
    }

    /** The content model of a declaration: its particles and its attributes. */
    record Content(List<Particle> particles, List<Attribute> attributes) {
        static final Content NONE = new Content(List.of(), List.of());
    }

    private final Ids ids;

    private ContentModelBuilder(Ids ids) {
        this.ids = ids;
    }

    /** The content model of a global declaration: an element (its anonymous type), a complexType, a group, an attributeGroup; none for the others. */
    static Content of(Element decl, Ids ids) {
        ContentModelBuilder b = new ContentModelBuilder(ids);
        return switch (decl.getLocalName()) {
            case XsdVocabulary.ELEMENT -> {
                Element anonymous = XsdParser.child(decl, XsdVocabulary.COMPLEX_TYPE);
                yield anonymous == null ? Content.NONE : b.complexType(anonymous);
            }
            case XsdVocabulary.COMPLEX_TYPE -> b.complexType(decl);
            case XsdVocabulary.GROUP, XsdVocabulary.ATTRIBUTE_GROUP -> b.model(decl);
            default -> Content.NONE;
        };
    }

    /** A complex type: the base type of a derivation first, then the particles and attributes of the type (or of its extension / restriction). */
    private Content complexType(Element type) {
        List<Particle> particles = new ArrayList<>();
        List<Attribute> attributes = new ArrayList<>();
        for (Element c : XsdParser.children(type)) {
            String ln = c.getLocalName();
            if (XsdVocabulary.SIMPLE_CONTENT.equals(ln) || XsdVocabulary.COMPLEX_CONTENT.equals(ln)) {
                for (Element d : XsdParser.children(c)) {
                    String kind = XsdVocabulary.EXTENSION.equals(d.getLocalName()) ? ParticleKind.EXTENDS
                            : XsdVocabulary.RESTRICTION.equals(d.getLocalName()) ? ParticleKind.RESTRICTS : null;
                    if (kind == null) continue;
                    if (d.hasAttribute(XsdVocabulary.ATTR_BASE)) {
                        String base = ids.type(d.getAttribute(XsdVocabulary.ATTR_BASE), d);
                        particles.add(new Particle(kind, SchemaGraph.nameOf(base), "", base, null, "", List.of(), List.of()));
                    }
                    Content own = model(d);
                    particles.addAll(own.particles());
                    attributes.addAll(own.attributes());
                }
            }
        }
        Content own = model(type);
        particles.addAll(own.particles());
        attributes.addAll(own.attributes());
        return new Content(particles, attributes);
    }

    /** The particles and attributes that are direct children of {@code e} (a type, an extension, a group, an attributeGroup). */
    private Content model(Element e) {
        List<Particle> particles = new ArrayList<>();
        List<Attribute> attributes = new ArrayList<>();
        for (Element c : XsdParser.children(e)) {
            switch (c.getLocalName()) {
                case XsdVocabulary.SEQUENCE, XsdVocabulary.CHOICE, XsdVocabulary.ALL -> particles.add(compositor(c));
                case XsdVocabulary.ELEMENT -> particles.add(element(c));
                case XsdVocabulary.GROUP -> particles.add(groupRef(c));
                case XsdVocabulary.ANY -> particles.add(any(c));
                case XsdVocabulary.ATTRIBUTE -> attributes.add(attribute(c));
                case XsdVocabulary.ATTRIBUTE_GROUP -> {
                    if (c.hasAttribute(XsdVocabulary.ATTR_REF)) {
                        String ref = ids.named(NodeKind.ATTRIBUTE_GROUP, c.getAttribute(XsdVocabulary.ATTR_REF), c);
                        attributes.add(new Attribute(SchemaGraph.nameOf(ref), ref, "", null));
                    }
                }
                case XsdVocabulary.ANY_ATTRIBUTE -> attributes.add(new Attribute(namespaceConstraint(c), "", "", null));
                default -> { }
            }
        }
        return new Content(particles, attributes);
    }

    private Particle compositor(Element c) {
        List<Particle> children = new ArrayList<>();
        for (Element p : XsdParser.children(c)) {
            switch (p.getLocalName()) {
                case XsdVocabulary.SEQUENCE, XsdVocabulary.CHOICE, XsdVocabulary.ALL -> children.add(compositor(p));
                case XsdVocabulary.ELEMENT -> children.add(element(p));
                case XsdVocabulary.GROUP -> children.add(groupRef(p));
                case XsdVocabulary.ANY -> children.add(any(p));
                default -> { }
            }
        }
        return new Particle(c.getLocalName(), "", "", "", XsdParser.particle(c), "", children, List.of());
    }

    /** A nested element: a reference to a global one, or its own (its name, its type, or its anonymous type walked). */
    private Particle element(Element e) {
        Cardinality card = XsdParser.particle(e);
        if (e.hasAttribute(XsdVocabulary.ATTR_REF)) {
            String ref = ids.named(NodeKind.ELEMENT, e.getAttribute(XsdVocabulary.ATTR_REF), e);
            return new Particle(ParticleKind.ELEMENT, SchemaGraph.nameOf(ref), ref, "", card, "", List.of(), List.of());
        }
        String name = e.getAttribute(XsdVocabulary.ATTR_NAME);
        String type = e.hasAttribute(XsdVocabulary.ATTR_TYPE) ? ids.type(e.getAttribute(XsdVocabulary.ATTR_TYPE), e) : "";
        Element anonymous = XsdParser.child(e, XsdVocabulary.COMPLEX_TYPE);
        Content own = anonymous == null ? Content.NONE : complexType(anonymous);
        return new Particle(ParticleKind.ELEMENT, name, "", type, card, "", own.particles(), own.attributes());
    }

    private Particle groupRef(Element g) {
        if (!g.hasAttribute(XsdVocabulary.ATTR_REF)) return compositor(g);   // a group declared inline (not valid XSD, but harmless)
        String ref = ids.named(NodeKind.GROUP, g.getAttribute(XsdVocabulary.ATTR_REF), g);
        return new Particle(ParticleKind.GROUP, SchemaGraph.nameOf(ref), ref, "", XsdParser.particle(g), "", List.of(), List.of());
    }

    private static Particle any(Element a) {
        return new Particle(ParticleKind.ANY, namespaceConstraint(a), "", "", XsdParser.particle(a), namespaceConstraint(a), List.of(), List.of());
    }

    private Attribute attribute(Element a) {
        Cardinality use = XsdParser.attributeUse(a);
        if (a.hasAttribute(XsdVocabulary.ATTR_REF)) {
            String ref = ids.named(NodeKind.ATTRIBUTE, a.getAttribute(XsdVocabulary.ATTR_REF), a);
            return new Attribute(SchemaGraph.nameOf(ref), ref, "", use);
        }
        String type = a.hasAttribute(XsdVocabulary.ATTR_TYPE) ? ids.type(a.getAttribute(XsdVocabulary.ATTR_TYPE), a) : "";
        return new Attribute(a.getAttribute(XsdVocabulary.ATTR_NAME), "", type, use);
    }

    private static String namespaceConstraint(Element wildcard) {
        return wildcard.hasAttribute(XsdVocabulary.ATTR_NAMESPACE) ? wildcard.getAttribute(XsdVocabulary.ATTR_NAMESPACE) : XsdVocabulary.NAMESPACE_ANY;
    }
}
