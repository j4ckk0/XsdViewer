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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.jtools.xsdviewer.model.Library.File;
import org.jtools.xsdviewer.schema.LinkLabel;
import org.jtools.xsdviewer.schema.NodeKind;
import org.jtools.xsdviewer.schema.ParticleKind;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaGraph.Attribute;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.jtools.xsdviewer.schema.SchemaGraph.Edge;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.jtools.xsdviewer.schema.SchemaGraph.Particle;
import org.junit.jupiter.api.Test;

/** The tree of a declaration, built from nodes made by hand: what the Model view draws and the comparison aligns. */
class ContentTreeTest {

    static final Cardinality ONE = Cardinality.ONE, MANY = new Cardinality(0, Cardinality.UNBOUNDED);

    /** A file made of {@code nodes} and {@code edges}, alone in its library. */
    static File file(List<Node> nodes, List<Edge> edges) {
        SchemaGraph g = new SchemaGraph();
        for (Node n : nodes) g.nodes.put(n.id(), n);
        g.edges.addAll(edges);
        return new File("a.xsd", g);
    }

    static Box tree(Node root, File file, String... expanded) {
        return ContentTree.build(root, file, new Library(List.of(file)), Set.of(expanded), false);
    }

    static Particle element(String name, String type, Cardinality card) {
        return new Particle(ParticleKind.ELEMENT, name, "", type, card, "", List.of(), List.of());
    }

    static Particle sequence(Particle... children) {
        return Particle.compositor(ParticleKind.SEQUENCE, ONE, List.of(children));
    }

    static Node type(String name, List<Particle> content, List<Attribute> attributes) {
        return new Node("complexType:" + name, NodeKind.COMPLEX_TYPE, name, "", 0, "").withContent(content, attributes);
    }

    static Node node(String kind, String name) {
        return new Node(kind + ":" + name, kind, name, "", 0, "");
    }

    final Node items = type("Items", List.of(sequence(element("item", "complexType:Item", MANY))), List.of());
    final Node item = type("Item", List.of(sequence(element("sku", "builtin:string", ONE))), List.of(Attribute.declared("partNum", "simpleType:SKU", ONE)));

    @Test
    void aCompositorIsABoxOfItsOwnHoldingTheElementsItHolds() {
        Box tree = tree(items, file(List.of(items, item), List.of()));
        assertEquals("Items", tree.name);
        assertTrue(tree.root);
        assertEquals(1, tree.children.size());
        assertEquals(ParticleKind.SEQUENCE, tree.children.get(0).kind);
        assertEquals("item", tree.children.get(0).children.get(0).name);
    }

    @Test
    void aNamedTypeIsAFoldedHandleUntilItsPathIsOpenedAndThenHoldsWhatThatTypeDeclares() {
        File f = file(List.of(items, item), List.of());
        Box folded = tree(items, f).children.get(0).children.get(0);
        assertEquals("Item", folded.typeName);
        assertTrue(folded.expandable);
        assertFalse(folded.expanded);
        assertEquals(0, folded.children.size());

        Box open = tree(items, f, "/0/0").children.get(0).children.get(0);
        assertTrue(open.expanded);
        assertEquals(ParticleKind.SEQUENCE, open.children.get(0).kind, "the content of the type, from its own declaration");
        assertEquals("sku", open.children.get(0).children.get(0).name);
        assertEquals("partNum", open.attributes.get(0).name);
        assertEquals("/0/0/a0", open.attributes.get(0).path);
    }

    @Test
    void aTypeAlreadyOpenAboveStopsRatherThanRepeatingItself() {
        Node tree = type("Tree", List.of(sequence(element("child", "complexType:Tree", MANY))), List.of());
        Box child = tree(tree, file(List.of(tree), List.of())).children.get(0).children.get(0);
        assertTrue(child.recursive);
        assertFalse(child.expandable, "nothing to open: its content is the one being drawn");
    }

    @Test
    void aTypeDeclaredNowhereAndASimpleTypeHaveNothingToOpen() {
        Node unknown = type("T", List.of(sequence(element("a", "type:Absent", ONE), element("b", "simpleType:SKU", ONE))), List.of());
        Node simple = node(NodeKind.SIMPLE_TYPE, "SKU");
        List<Box> boxes = tree(unknown, file(List.of(unknown, simple), List.of())).children.get(0).children;
        assertEquals("Absent", boxes.get(0).typeName, "named after the id, the declaration being nowhere");
        assertFalse(boxes.get(0).expandable);
        assertFalse(boxes.get(1).expandable, "a simple type holds no particle");
    }

    @Test
    void aGlobalElementTakesTheContentModelOfTheTypeItIsOf() {
        Node element = node(NodeKind.ELEMENT, "purchaseOrder");
        Box tree = tree(element, file(List.of(element, items, item), List.of(new Edge(element.id(), items.id(), LinkLabel.TYPE))));
        assertEquals(ParticleKind.SEQUENCE, tree.children.get(0).kind);
        assertEquals("item", tree.children.get(0).children.get(0).name);
    }

    @Test
    void anAnonymousTypeIsDrawnInPlaceWithTheAttributesItDeclares() {
        Particle inline = new Particle(ParticleKind.ELEMENT, "inline", "", "", ONE, "",
                List.of(sequence(element("deep", "builtin:string", ONE))), List.of(Attribute.declared("lang", "builtin:string", Cardinality.OPTIONAL)));
        Node holder = type("Holder", List.of(sequence(inline)), List.of());
        Box box = tree(holder, file(List.of(holder), List.of())).children.get(0).children.get(0);
        assertFalse(box.expandable, "nothing to open: its content is already here");
        assertEquals("deep", box.children.get(0).children.get(0).name);
        assertEquals("lang", box.attributes.get(0).name);
    }

    @Test
    void aNamedTypeDeclaredInAnotherFileOfTheLibraryOpensFromThere() {
        Node placeholder = new Node("type:Item", NodeKind.EXTERNAL, "Item", "", 0, "");
        File home = file(List.of(items, placeholder), List.of());
        SchemaGraph other = new SchemaGraph();
        other.nodes.put(item.id(), item);
        File elsewhere = new File("b.xsd", other);
        Box open = ContentTree.build(items, home, new Library(List.of(home, elsewhere)), Set.of(), true).children.get(0).children.get(0);
        assertEquals("Item", open.typeName);
        assertTrue(open.expanded);
        assertEquals("sku", open.children.get(0).children.get(0).name, "read from the file that declares it");
    }

    // --- a WSDL's or a Schematron's own objects: no content model, but a chain, which is their model

    final Node service = node(NodeKind.SERVICE, "PurchaseOrderService");
    final Node portType = node(NodeKind.PORT_TYPE, "PurchaseOrderPortType");
    final Node operation = node(NodeKind.OPERATION, "submitPurchaseOrder");
    final List<Edge> chain = List.of(new Edge(service.id(), portType.id(), "PurchaseOrderPort"), new Edge(portType.id(), operation.id(), LinkLabel.OPERATION));

    @Test
    void aServiceHasItsChainForAModelABoxNamedAfterWhatTheLinkLeadsTo() {
        Box tree = tree(service, file(List.of(service, portType, operation), chain));
        assertEquals(1, tree.children.size());
        Box port = tree.children.get(0);
        assertEquals("PurchaseOrderPortType", port.name, "named after what it leads to");
        assertEquals("PurchaseOrderPort", port.word, "the link is the port");
        assertEquals(NodeKind.PORT_TYPE, port.kind);
        assertEquals(portType.id(), port.ref, "a click selects it");
        assertTrue(port.expandable);
        assertEquals(0, port.children.size(), "folded until its path is opened");
    }

    @Test
    void aChainOpensOneLevelAtATimeAsAContentModelDoes() {
        Box tree = tree(service, file(List.of(service, portType, operation), chain), "/0");
        Box op = tree.children.get(0).children.get(0);
        assertEquals("submitPurchaseOrder", op.name);
        assertEquals(LinkLabel.OPERATION, op.word);
        assertFalse(op.expandable, "the operation carries nothing further here");
    }

    @Test
    void whereAChainReachesTheSchemaTheContentModelTakesOver() {
        Node message = node(NodeKind.MESSAGE, "SubmitPurchaseOrderRequest");
        Node element = new Node("element:purchaseOrder", NodeKind.ELEMENT, "purchaseOrder", "", 0, "")
                .withContent(List.of(sequence(element("shipTo", "complexType:USAddress", ONE))), List.of());
        Box tree = tree(message, file(List.of(message, element), List.of(new Edge(message.id(), element.id(), "order"))), "/0");
        Box part = tree.children.get(0);
        assertEquals("order", part.word, "the part names the link");
        assertEquals(NodeKind.ELEMENT, part.kind);
        assertEquals(ParticleKind.SEQUENCE, part.children.get(0).kind, "the element's content model, not a chain");
        assertEquals("shipTo", part.children.get(0).children.get(0).name);
    }

    @Test
    void aDeclarationThatNamesNothingElseDrawsNothing() {
        Node simple = node(NodeKind.SIMPLE_TYPE, "SKU");
        Box tree = tree(simple, file(List.of(simple), List.of()));
        assertEquals(0, tree.children.size());
        assertEquals(0, tree.attributes.size());
    }
}
