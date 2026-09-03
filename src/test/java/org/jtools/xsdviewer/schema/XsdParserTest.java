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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
}
