package org.jtools.xsdviewer.compare;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.jtools.xsdviewer.compare.ModelDiff.Counts;
import org.jtools.xsdviewer.model.Box;
import org.jtools.xsdviewer.model.ContentTree;
import org.jtools.xsdviewer.model.Library;
import org.jtools.xsdviewer.model.Library.File;
import org.jtools.xsdviewer.schema.NodeKind;
import org.jtools.xsdviewer.schema.ParticleKind;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaGraph.Attribute;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.jtools.xsdviewer.schema.SchemaGraph.Particle;
import org.junit.jupiter.api.Test;

class ModelDiffTest {

    static final Cardinality ONE = Cardinality.ONE;

    static Particle element(String name, String type) {
        return element(name, type, ONE);
    }

    static Particle element(String name, String type, Cardinality card) {
        return new Particle(ParticleKind.ELEMENT, name, "", type, card, "", List.of(), List.of());
    }

    static Particle sequence(Particle... children) {
        return Particle.compositor(ParticleKind.SEQUENCE, ONE, List.of(children));
    }

    /** A complexType whose content is one sequence of {@code children}, with {@code attributes}. */
    static Node type(String name, List<Particle> children, List<Attribute> attributes) {
        return new Node("complexType:" + name, NodeKind.COMPLEX_TYPE, name, "", 0, "").withContent(List.of(sequence(children.toArray(Particle[]::new))), attributes);
    }

    static Node type(String name, Particle... children) {
        return type(name, List.of(children), List.of());
    }

    /** The whole tree of {@code root} in a file made of {@code nodes}. */
    static Box tree(Node root, Node... nodes) {
        SchemaGraph g = new SchemaGraph();
        for (Node n : nodes) g.nodes.put(n.id(), n);
        File f = new File("a.xsd", g);
        return ContentTree.build(root, f, new Library(List.of(f)), Set.of(), true);
    }

    record Compared(Box left, Box right, Counts counts) {}

    static Compared compare(Node left, Node right) {
        Box l = tree(left, left), r = tree(right, right);
        return new Compared(l, r, ModelDiff.mark(l, r));
    }

    static Box named(Box tree, String name) {
        return tree.all().stream().filter(b -> b.name.equals(name)).findFirst().orElseThrow();
    }

    @Test
    void twoIdenticalModelsDifferInNothing() {
        Compared c = compare(type("T", element("a", "builtin:string")), type("T", element("a", "builtin:string")));
        assertTrue(c.counts().same());
        assertEquals(3, c.counts().same, "the type, its sequence and its element");
    }

    @Test
    void anElementOnOneSideOnlyIsRemovedAndTheOtherStaysSame() {
        Compared c = compare(type("T", element("a", "builtin:string"), element("gone", "builtin:string")), type("T", element("a", "builtin:string")));
        assertEquals(ModelDiff.REMOVED, named(c.left(), "gone").diff);
        assertEquals(ModelDiff.SAME, named(c.left(), "a").diff);
        assertEquals(1, c.counts().removed);
        assertEquals(0, c.counts().added);
    }

    @Test
    void anElementInsertedOnOneSideDoesNotShiftTheBoxesBelowIt() {
        Compared c = compare(type("T", element("a", "builtin:string"), element("c", "builtin:string")),
                type("T", element("a", "builtin:string"), element("b", "builtin:string"), element("c", "builtin:string")));
        assertEquals(ModelDiff.ADDED, named(c.right(), "b").diff);
        assertEquals(ModelDiff.SAME, named(c.right(), "c").diff, "matched by what it is, not by where it sits");
        assertEquals(ModelDiff.SAME, named(c.left(), "c").diff);
        assertEquals(1, c.counts().added);
        assertEquals(0, c.counts().removed);
    }

    @Test
    void occurrencesThatDisagreeMakeTheBoxChangedNotOneRemovedAndOneAdded() {
        Compared c = compare(type("T", element("a", "builtin:string", Cardinality.OPTIONAL)), type("T", element("a", "builtin:string", new Cardinality(1, Cardinality.UNBOUNDED))));
        assertEquals(ModelDiff.CHANGED, named(c.left(), "a").diff);
        assertEquals(ModelDiff.CHANGED, named(c.right(), "a").diff);
        assertEquals(1, c.counts().changed);
        assertFalse(c.counts().same());
    }

    @Test
    void aTypeThatChangedMakesTheBoxChanged() {
        Compared c = compare(type("T", element("a", "builtin:string")), type("T", element("a", "builtin:decimal")));
        assertEquals(ModelDiff.CHANGED, named(c.left(), "a").diff);
        assertEquals(1, c.counts().changed);
    }

    @Test
    void attributesAreComparedAsElementsAre() {
        Compared c = compare(
                type("T", List.of(), List.of(Attribute.declared("kept", "builtin:string", ONE), Attribute.declared("gone", "builtin:string", Cardinality.OPTIONAL))),
                type("T", List.of(), List.of(Attribute.declared("kept", "builtin:string", ONE), Attribute.declared("new", "builtin:string", Cardinality.OPTIONAL))));
        assertEquals(ModelDiff.REMOVED, named(c.left(), "gone").diff);
        assertEquals(ModelDiff.ADDED, named(c.right(), "new").diff);
        assertEquals(ModelDiff.SAME, named(c.left(), "kept").diff);
    }

    @Test
    void aCompositorThatChangedCarriesWhatItHolds() {
        Node left = new Node("complexType:T", NodeKind.COMPLEX_TYPE, "T", "", 0, "")
                .withContent(List.of(Particle.compositor(ParticleKind.CHOICE, ONE, List.of(element("a", "builtin:string")))), List.of());
        Node right = type("T", element("a", "builtin:string"));   // a sequence instead of a choice
        Compared c = compare(left, right);
        assertEquals(2, c.counts().removed, "the choice and its element");
        assertEquals(2, c.counts().added, "the sequence and its element");
    }

    @Test
    void aDeclarationOnOneSideOnlyIsWhollyRemoved() {
        Box l = tree(type("T", element("a", "builtin:string")));
        Counts counts = ModelDiff.mark(l, null);
        assertEquals(ModelDiff.REMOVED, l.diff);
        assertEquals(3, counts.removed);
        assertFalse(counts.same());
    }

    @Test
    void aNamedTypeIsOpenedOnBothSidesSoAChangeInsideItIsSeen() {
        Node outer = new Node("complexType:Outer", NodeKind.COMPLEX_TYPE, "Outer", "", 0, "")
                .withContent(List.of(sequence(element("held", "complexType:Inner"))), List.of());
        Node innerString = new Node("complexType:Inner", NodeKind.COMPLEX_TYPE, "Inner", "", 0, "").withContent(List.of(sequence(element("deep", "builtin:string"))), List.of());
        Node innerDecimal = new Node("complexType:Inner", NodeKind.COMPLEX_TYPE, "Inner", "", 0, "").withContent(List.of(sequence(element("deep", "builtin:decimal"))), List.of());
        Box l = tree(outer, outer, innerString), r = tree(outer, outer, innerDecimal);
        Counts counts = ModelDiff.mark(l, r);
        assertEquals(ModelDiff.CHANGED, named(l, "deep").diff, "the element of the named type, opened on both sides");
        assertEquals(1, counts.changed);
    }

    @Test
    void aMatchedPairSharesOneFoldKeyAndASideOnlyBoxCarriesItsSide() {
        Compared c = compare(type("T", element("a", "builtin:string"), element("gone", "builtin:string")), type("T", element("a", "builtin:string")));
        assertEquals(named(c.left(), "a").foldKey, named(c.right(), "a").foldKey);
        assertTrue(named(c.left(), "gone").foldKey.startsWith(ModelDiff.REMOVED + ":"));
    }
}
