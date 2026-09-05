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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class XsdParserTest {

    private static SchemaGraph model;

    @BeforeAll
    static void parseSample() throws Exception {
        model = SchemaParser.parse(Files.readString(Path.of("samples/purchaseOrder.xsd")));
    }

    private static boolean hasEdge(SchemaGraph g, String from, String to, String label) {
        return g.edges.stream().anyMatch(e -> e.from().equals(from) && e.to().equals(to) && e.label().equals(label));
    }

    private static boolean hasEdge(String from, String to, String label) {
        return hasEdge(model, from, to, label);
    }

    private static SchemaGraph.Cardinality cardinality(SchemaGraph g, String from, String to, String label) {
        return g.edges.stream().filter(e -> e.from().equals(from) && e.to().equals(to) && e.label().equals(label))
                .findFirst().orElseThrow().cardinality();
    }

    @Test
    void globalDeclarationsBecomeNodes() {
        Set<String> declared = model.nodes.values().stream()
                .filter(n -> !n.kind().equals(NodeKind.BUILTIN) && !n.kind().equals(NodeKind.EXTERNAL))
                .map(SchemaGraph.Node::id).collect(Collectors.toSet());
        assertEquals(Set.of(
                "element:purchaseOrder", "element:comment", "element:urgentComment",
                "complexType:PurchaseOrderType", "complexType:USAddress", "complexType:Items",
                "complexType:InternationalAddress", "complexType:Category",
                "group:ItemExtras", "attributeGroup:AuditAttributes", "attribute:version",
                "simpleType:SKU", "simpleType:Message", "simpleType:LimitedText",
                "simpleType:SKUList", "simpleType:Identifier", "simpleType:Currency"), declared);
    }

    @Test
    void membersOfADeclaration() {
        List<String> members = model.nodes.get("complexType:PurchaseOrderType").members();
        assertTrue(members.containsAll(List.of("shipTo", "billTo", "comment", "items", "orderDate")), "nested elements, a ref (its local name), an attribute: " + members);
        assertTrue(model.nodes.get("complexType:Items").members().contains("partNum"), "an attribute of a nested element's anonymous type");
        assertTrue(model.nodes.get("simpleType:SKU").members().isEmpty());
        assertTrue(model.nodes.get("element:purchaseOrder").members().isEmpty(), "a type reference has no members of its own");
    }

    @Test
    void keyrefsAndWildcards() throws Exception {
        SchemaGraph m = SchemaParser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:t" targetNamespace="urn:t">
                  <xs:element name="library">
                    <xs:complexType><xs:sequence>
                      <xs:element name="book" maxOccurs="unbounded"/><xs:any namespace="##other" processContents="lax"/>
                    </xs:sequence><xs:anyAttribute/></xs:complexType>
                    <xs:key name="bookId"><xs:selector xpath="t:book"/><xs:field xpath="@id"/></xs:key>
                  </xs:element>
                  <xs:element name="loan">
                    <xs:complexType><xs:sequence><xs:element name="of" type="xs:string"/></xs:sequence></xs:complexType>
                    <xs:keyref name="loanOfBook" refer="t:bookId"><xs:selector xpath="."/><xs:field xpath="@book"/></xs:keyref>
                  </xs:element>
                </xs:schema>""");
        assertTrue(hasEdge(m, "element:loan", "element:library", "keyref loanOfBook"));
        assertEquals(List.of("book", "any (##other)", "anyAttribute (##any)"), m.nodes.get("element:library").members());
    }

    @Test
    void enumerationValues() throws Exception {
        assertEquals(List.of(new SchemaGraph.Value("USD", "US dollar"), new SchemaGraph.Value("EUR", "Euro"), new SchemaGraph.Value("GBP", "")),
                model.nodes.get("simpleType:Currency").values());
        assertTrue(model.nodes.get("simpleType:SKU").values().isEmpty(), "a pattern restriction enumerates nothing");
        assertTrue(model.nodes.get("complexType:Items").values().isEmpty(), "the enumeration of a nested element is not its container's");
        // an element and an attribute with an anonymous enumerated type, a complexType with an enumerated simpleContent
        SchemaGraph m = SchemaParser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="status"><xs:simpleType><xs:restriction base="xs:string">
                    <xs:enumeration value="open"/><xs:enumeration value="closed"/>
                  </xs:restriction></xs:simpleType></xs:element>
                  <xs:attribute name="unit"><xs:simpleType><xs:restriction base="xs:string"><xs:enumeration value="kg"/></xs:restriction></xs:simpleType></xs:attribute>
                  <xs:complexType name="Code"><xs:simpleContent><xs:restriction base="xs:string"><xs:enumeration value="A"/></xs:restriction></xs:simpleContent></xs:complexType>
                  <xs:complexType name="Wrapper"><xs:sequence><xs:element ref="status"/></xs:sequence></xs:complexType>
                </xs:schema>""");
        assertEquals(List.of("open", "closed"), m.nodes.get("element:status").values().stream().map(SchemaGraph.Value::value).toList());
        assertEquals(List.of("kg"), m.nodes.get("attribute:unit").values().stream().map(SchemaGraph.Value::value).toList());
        assertEquals(List.of("A"), m.nodes.get("complexType:Code").values().stream().map(SchemaGraph.Value::value).toList());
        assertTrue(m.nodes.get("complexType:Wrapper").values().isEmpty());
    }

    @Test
    void schemaHeader() {
        assertEquals("http://example.com/po", model.targetNamespace);
        assertEquals(List.of(new SchemaGraph.Import(XsdVocabulary.IMPORT, "http://example.com/ext", "ext.xsd")), model.imports);
    }

    @Test
    void elementLinks() {
        assertTrue(hasEdge("element:purchaseOrder", "complexType:PurchaseOrderType", "type"));
        assertTrue(hasEdge("element:comment", "builtin:string", "type"));
        assertTrue(hasEdge("element:urgentComment", "element:comment", "substitutes"));
    }

    @Test
    void complexTypeLinks() {
        assertTrue(hasEdge("complexType:PurchaseOrderType", "complexType:USAddress", "shipTo"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "complexType:USAddress", "billTo"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "element:comment", "ref"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "complexType:Items", "items"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "builtin:date", "attribute orderDate"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "attributeGroup:AuditAttributes", "attributeGroup"));
        assertTrue(hasEdge("complexType:InternationalAddress", "complexType:USAddress", "extends"));
    }

    @Test
    void nestedAnonymousTypesAreAttributedToTheGlobalOwner() {
        assertTrue(hasEdge("complexType:Items", "builtin:string", "productName"));
        assertTrue(hasEdge("complexType:Items", "builtin:positiveInteger", "restricts"));
        assertTrue(hasEdge("complexType:Items", "simpleType:SKU", "attribute partNum"));
        assertTrue(hasEdge("complexType:Items", "group:ItemExtras", "group"));
        assertTrue(hasEdge("complexType:Items", "element:comment", "ref"));
    }

    @Test
    void groupAndAttributeGroupLinks() {
        assertTrue(hasEdge("group:ItemExtras", "simpleType:Message", "giftMessage"));
        assertTrue(hasEdge("attributeGroup:AuditAttributes", "attribute:version", "attribute ref"));
        assertTrue(hasEdge("attribute:version", "builtin:token", "type"));
    }

    @Test
    void simpleTypeLinks() {
        assertTrue(hasEdge("simpleType:SKU", "builtin:string", "restricts"));
        assertTrue(hasEdge("simpleType:Message", "simpleType:LimitedText", "restricts"));
        assertTrue(hasEdge("simpleType:SKUList", "simpleType:SKU", "list of"));
        assertTrue(hasEdge("simpleType:Identifier", "simpleType:SKU", "union of"));
        assertTrue(hasEdge("simpleType:Identifier", "builtin:positiveInteger", "union of"));
    }

    @Test
    void recursiveTypeLinksToItself() {
        assertTrue(hasEdge("complexType:Category", "complexType:Category", "subCategory"));
    }

    @Test
    void undeclaredReferencesBecomeExternalNodes() {
        SchemaGraph.Node ext = model.nodes.get("type:Label");
        assertEquals(NodeKind.EXTERNAL, ext.kind());
        assertEquals("Label", ext.name());
        assertTrue(hasEdge("complexType:Category", "type:Label", "attribute label"));
    }

    @Test
    void builtinTypesAreSharedNodes() {
        assertEquals(NodeKind.BUILTIN, model.nodes.get("builtin:string").kind());
        assertEquals(1, model.nodes.values().stream().filter(n -> n.name().equals("string")).count());
    }

    @Test
    void lineNumbersPointAtTheStartTag() {
        assertEquals(18, model.nodes.get("element:purchaseOrder").line());
        assertEquals(28, model.nodes.get("complexType:PurchaseOrderType").line());
        // start tag spread over two lines: the line of '<', not of the name attribute
        assertEquals(102, model.nodes.get("simpleType:LimitedText").line());
        assertEquals(0, model.nodes.get("builtin:string").line());
    }

    @Test
    void documentationIsExtracted() {
        assertEquals("Root element of a purchase order document.", model.nodes.get("element:purchaseOrder").doc());
        assertEquals("Stock Keeping Unit, e.g. 123-AB.", model.nodes.get("simpleType:SKU").doc());
        assertEquals("", model.nodes.get("complexType:USAddress").doc());
    }

    @Test
    void rejectsNonSchemaXml() {
        Exception e = assertThrows(Exception.class, () -> SchemaParser.parse("<root><a/></root>"));
        assertEquals(Messages.get(MessageKey.NOT_A_SCHEMA, "root"), e.getMessage());
        assertThrows(Exception.class, () -> SchemaParser.parse("not xml at all"));
    }

    @Test
    void defaultNamespaceSchemasResolveBuiltins() throws Exception {
        SchemaGraph m = SchemaParser.parse("""
                <schema xmlns="http://www.w3.org/2001/XMLSchema">
                  <element name="a" type="string"/>
                  <element name="b" type="T"/>
                  <complexType name="T"><sequence><element ref="a"/></sequence></complexType>
                </schema>""");
        assertTrue(hasEdge(m, "element:a", "builtin:string", "type"));
        assertTrue(hasEdge(m, "element:b", "complexType:T", "type"));
        assertTrue(hasEdge(m, "complexType:T", "element:a", "ref"));
    }

    @Test
    void cardinalitiesOfElementsGroupsAndAttributes() {
        SchemaGraph.Cardinality one = SchemaGraph.Cardinality.ONE, optional = SchemaGraph.Cardinality.OPTIONAL;
        assertEquals(one, cardinality(model, "complexType:PurchaseOrderType", "complexType:USAddress", "shipTo"));
        assertEquals(optional, cardinality(model, "complexType:PurchaseOrderType", "element:comment", "ref"));
        assertEquals(optional, cardinality(model, "complexType:PurchaseOrderType", "builtin:date", "attribute orderDate"));
        assertNull(cardinality(model, "complexType:PurchaseOrderType", "attributeGroup:AuditAttributes", "attributeGroup"));
        assertEquals(new SchemaGraph.Cardinality(0, SchemaGraph.Cardinality.UNBOUNDED), cardinality(model, "complexType:Category", "complexType:Category", "subCategory"));
        // inside item (0..*): counted from item, not from Items
        assertEquals(one, cardinality(model, "complexType:Items", "builtin:string", "productName"));
        assertEquals(one, cardinality(model, "complexType:Items", "simpleType:SKU", "attribute partNum"));
        assertEquals(optional, cardinality(model, "complexType:Items", "group:ItemExtras", "group"));
        assertEquals(optional, cardinality(model, "attributeGroup:AuditAttributes", "attribute:version", "attribute ref"));
        // type links have none
        assertNull(cardinality(model, "element:purchaseOrder", "complexType:PurchaseOrderType", "type"));
        assertNull(cardinality(model, "complexType:InternationalAddress", "complexType:USAddress", "extends"));
        assertNull(cardinality(model, "simpleType:SKUList", "simpleType:SKU", "list of"));
    }

    @Test
    void theContentModelOfADeclaration() {
        // Items: a sequence holding item (0..*), whose anonymous type holds a sequence of elements, a reference, a group reference, and an attribute
        SchemaGraph.Node items = model.nodes.get("complexType:Items");
        assertEquals(1, items.content().size());
        SchemaGraph.Particle seq = items.content().get(0);
        assertEquals(ParticleKind.SEQUENCE, seq.kind());
        assertEquals(SchemaGraph.Cardinality.ONE, seq.cardinality());
        SchemaGraph.Particle item = seq.children().get(0);
        assertEquals(ParticleKind.ELEMENT, item.kind());
        assertEquals("item", item.name());
        assertEquals(new SchemaGraph.Cardinality(0, SchemaGraph.Cardinality.UNBOUNDED), item.cardinality());
        assertEquals("", item.type(), "an anonymous type: walked in place");
        SchemaGraph.Particle inner = item.children().get(0);
        assertEquals(List.of("productName", "quantity", "USPrice", "comment", "shipDate", "ItemExtras"), inner.children().stream().map(SchemaGraph.Particle::name).toList());
        assertEquals("builtin:string", inner.children().get(0).type());
        assertEquals("", inner.children().get(1).type(), "quantity has an anonymous simple type");
        assertEquals("element:comment", inner.children().get(3).ref());
        SchemaGraph.Particle extras = inner.children().get(5);
        assertEquals(ParticleKind.GROUP, extras.kind());
        assertEquals("group:ItemExtras", extras.ref());
        assertEquals(1, item.attributes().size());
        assertEquals("partNum", item.attributes().get(0).name());
        assertEquals("simpleType:SKU", item.attributes().get(0).type());
        assertEquals(SchemaGraph.Cardinality.ONE, item.attributes().get(0).use());
        // a derived type: the base first, then its own particles
        SchemaGraph.Node international = model.nodes.get("complexType:InternationalAddress");
        assertEquals(ParticleKind.EXTENDS, international.content().get(0).kind());
        assertEquals("complexType:USAddress", international.content().get(0).type());
        assertEquals(ParticleKind.SEQUENCE, international.content().get(1).kind());
        // a global element of a named type has no content of its own; a simple type neither; a group has its compositor; an attributeGroup its attributes
        assertTrue(model.nodes.get("element:purchaseOrder").content().isEmpty());
        assertTrue(model.nodes.get("simpleType:SKU").content().isEmpty());
        assertEquals(ParticleKind.SEQUENCE, model.nodes.get("group:ItemExtras").content().get(0).kind());
        assertEquals(List.of("createdBy", "version"), model.nodes.get("attributeGroup:AuditAttributes").attributes().stream().map(SchemaGraph.Attribute::name).toList());
        assertEquals("attribute:version", model.nodes.get("attributeGroup:AuditAttributes").attributes().get(1).ref());
        // the JSON: content and attributes only where there are some
        String json = model.toJson();
        assertTrue(json.contains("\"name\":\"item\",\"min\":0,\"max\":-1,\"children\":[{\"kind\":\"sequence\""), json);
        assertTrue(json.contains("\"attributes\":[{\"name\":\"partNum\",\"type\":\"simpleType:SKU\",\"min\":1,\"max\":1}]"), json);
        assertFalse(json.contains("\"id\":\"simpleType:SKU\",\"kind\":\"simpleType\",\"name\":\"SKU\",\"ns\":\"http://example.com/po\",\"line\":89,\"doc\":\"Stock Keeping Unit, e.g. 123-AB.\",\"content\""));
    }

    @Test
    void theCompositorOfANestedElement() throws Exception {
        SchemaGraph m = SchemaParser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="T">
                    <xs:sequence>
                      <xs:element name="a" type="xs:string"/>
                      <xs:choice>
                        <xs:element name="b" type="xs:string"/>
                        <xs:group ref="G"/>
                      </xs:choice>
                      <xs:element name="c">
                        <xs:complexType><xs:all><xs:element name="d" type="xs:string"/></xs:all></xs:complexType>
                      </xs:element>
                    </xs:sequence>
                    <xs:attribute name="p" type="xs:string"/>
                  </xs:complexType>
                  <xs:element name="e" type="xs:string"/>
                </xs:schema>""");
        assertEquals("sequence", compositor(m, "complexType:T", "a"));
        assertEquals("choice", compositor(m, "complexType:T", "b"));
        assertEquals("choice", compositor(m, "complexType:T", LinkLabel.GROUP), "a group reference is a branch like an element");
        assertEquals("all", compositor(m, "complexType:T", "d"), "the content of c's own type sits in its own compositor, not in the sequence holding c");
        assertEquals("", compositor(m, "complexType:T", LinkLabel.attribute("p")), "an attribute sits in no compositor");
        assertEquals("", compositor(m, "element:e", LinkLabel.TYPE), "a declaration's own type link has none");
    }

    /** The compositor of the link of {@code owner} labelled {@code label}. */
    private static String compositor(SchemaGraph g, String owner, String label) {
        return g.edges.stream().filter(e -> e.from().equals(owner) && e.label().equals(label))
                .map(SchemaGraph.Edge::compositor).findFirst().orElseThrow(() -> new AssertionError("no link " + label + " from " + owner));
    }

    @Test
    void enclosingCompositorsCountToo() throws Exception {
        SchemaGraph m = SchemaParser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="T">
                    <xs:sequence minOccurs="0" maxOccurs="unbounded">
                      <xs:element name="a" type="xs:string"/>
                      <xs:choice maxOccurs="2">
                        <xs:element name="b" type="xs:string"/>
                        <xs:element name="c" type="xs:string" minOccurs="2" maxOccurs="3"/>
                      </xs:choice>
                    </xs:sequence>
                    <xs:attribute name="p" type="xs:string" use="prohibited"/>
                  </xs:complexType>
                </xs:schema>""");
        assertEquals(new SchemaGraph.Cardinality(0, SchemaGraph.Cardinality.UNBOUNDED), cardinality(m, "complexType:T", "builtin:string", "a"));
        assertEquals(new SchemaGraph.Cardinality(0, SchemaGraph.Cardinality.UNBOUNDED), cardinality(m, "complexType:T", "builtin:string", "b"));
        assertEquals(new SchemaGraph.Cardinality(0, SchemaGraph.Cardinality.UNBOUNDED), cardinality(m, "complexType:T", "builtin:string", "c"));
        assertEquals(SchemaGraph.Cardinality.NONE, cardinality(m, "complexType:T", "builtin:string", "attribute p"));
        SchemaGraph m2 = SchemaParser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="T">
                    <xs:choice maxOccurs="2">
                      <xs:element name="b" type="xs:string"/>
                      <xs:element name="c" type="xs:string" minOccurs="2" maxOccurs="3"/>
                    </xs:choice>
                  </xs:complexType>
                </xs:schema>""");
        assertEquals(new SchemaGraph.Cardinality(0, 2), cardinality(m2, "complexType:T", "builtin:string", "b"));
        assertEquals(new SchemaGraph.Cardinality(0, 6), cardinality(m2, "complexType:T", "builtin:string", "c"));
    }

    @Test
    void aDeclarationSpansFromItsStartTagToItsEndTag() throws Exception {
        // the lines a declaration covers: what the comparison reads to show two of them as text
        SchemaGraph m = SchemaParser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:s">
                  <xs:complexType name="T">
                    <xs:sequence>
                      <xs:element name="a" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                  <xs:element name="e" type="xs:string"/>
                </xs:schema>""");
        SchemaGraph.Node type = m.nodes.get("complexType:T");
        assertEquals(2, type.line());
        assertEquals(6, type.endLine());
        SchemaGraph.Node element = m.nodes.get("element:e");   // self-closed: it starts and ends on its line
        assertEquals(7, element.line());
        assertEquals(7, element.endLine());
        assertEquals(0, m.nodes.get("builtin:string").endLine());   // not declared here
    }

    @Test
    void contentModelsNameWhatTheLinksName() {
        assertContentNamesLinkedNodes(model);
    }

    @Test
    void theContentModelNamesATypeOfAnotherInlineSchemaAsTheLinksDo() throws Exception {
        // the schemas inline in a WSDL share one graph, parsed one after the other: what the first
        // says of a type the second declares is only known once every schema has been read
        SchemaGraph m = SchemaParser.parse("""
                <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:b="urn:b">
                  <wsdl:types>
                    <xs:schema targetNamespace="urn:a">
                      <xs:complexType name="Holder">
                        <xs:sequence><xs:element name="held" type="b:Held"/></xs:sequence>
                        <xs:attribute name="of" type="b:Kind"/>
                      </xs:complexType>
                    </xs:schema>
                    <xs:schema targetNamespace="urn:b">
                      <xs:complexType name="Held"><xs:sequence><xs:element name="v" type="xs:string"/></xs:sequence></xs:complexType>
                      <xs:simpleType name="Kind"><xs:restriction base="xs:string"/></xs:simpleType>
                    </xs:schema>
                  </wsdl:types>
                </wsdl:definitions>""");
        SchemaGraph.Node holder = m.nodes.get("complexType:Holder");
        assertEquals("complexType:Held", holder.content().get(0).children().get(0).type());
        assertEquals("simpleType:Kind", holder.attributes().get(0).type());
        assertTrue(hasEdge(m, "complexType:Holder", "complexType:Held", "held"));
        assertContentNamesLinkedNodes(m);
    }

    /**
     * Every node a content model names — the type of a particle or of an attribute, what a reference
     * refers to — is a node of the graph and the target of a link of that same declaration: the two
     * walks over the XSD (the links, the content model) name the same things, which is what lets the
     * Model view open a box from the graph's nodes.
     */
    private static void assertContentNamesLinkedNodes(SchemaGraph g) {
        for (SchemaGraph.Node n : g.nodes.values()) {
            Set<String> targets = g.edges.stream().filter(e -> e.from().equals(n.id())).map(SchemaGraph.Edge::to).collect(Collectors.toSet());
            for (String named : namedNodes(n.content(), n.attributes())) {
                assertTrue(g.declares(named), named + ", named by the content model of " + n.id() + ", is not a node of the graph");
                assertTrue(targets.contains(named), named + ", named by the content model of " + n.id() + ", is the target of no link of that declaration");
            }
        }
    }

    /** The nodes a content model names, down the tree: the type of each particle and of each attribute, and what each reference refers to. */
    private static List<String> namedNodes(List<SchemaGraph.Particle> particles, List<SchemaGraph.Attribute> attributes) {
        List<String> named = new ArrayList<>();
        for (SchemaGraph.Particle p : particles) {
            if (!p.type().isEmpty()) named.add(p.type());
            if (!p.ref().isEmpty()) named.add(p.ref());
            named.addAll(namedNodes(p.children(), p.attributes()));
        }
        for (SchemaGraph.Attribute a : attributes) {
            if (!a.type().isEmpty()) named.add(a.type());
            if (!a.ref().isEmpty()) named.add(a.ref());
        }
        return named;
    }
}
