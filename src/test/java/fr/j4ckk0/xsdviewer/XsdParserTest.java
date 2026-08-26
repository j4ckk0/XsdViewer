package fr.j4ckk0.xsdviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class XsdParserTest {

    private static Model model;

    @BeforeAll
    static void parseSample() throws Exception {
        model = XsdParser.parse(Files.readString(Path.of("samples/purchaseOrder.xsd")));
    }

    private static boolean hasEdge(String from, String to, String label) {
        return model.edges.contains(new Model.Edge(from, to, label));
    }

    @Test
    void globalDeclarationsBecomeNodes() {
        Set<String> declared = model.nodes.values().stream()
                .filter(n -> !n.kind().equals("builtin") && !n.kind().equals("external"))
                .map(Model.Node::id).collect(Collectors.toSet());
        assertEquals(Set.of(
                "element:purchaseOrder", "element:comment", "element:urgentComment",
                "complexType:PurchaseOrderType", "complexType:USAddress", "complexType:Items",
                "complexType:InternationalAddress", "complexType:Category",
                "group:ItemExtras", "attributeGroup:AuditAttributes", "attribute:version",
                "simpleType:SKU", "simpleType:Message", "simpleType:LimitedText",
                "simpleType:SKUList", "simpleType:Identifier"), declared);
    }

    @Test
    void schemaHeader() {
        assertEquals("http://example.com/po", model.targetNamespace);
        assertTrue(model.imports.isEmpty());
    }

    @Test
    void elementLinks() {
        assertTrue(hasEdge("element:purchaseOrder", "complexType:PurchaseOrderType", "type"));
        assertTrue(hasEdge("element:comment", "builtin:string", "type"));
        assertTrue(hasEdge("element:urgentComment", "element:comment", "substitutes"));
    }

    @Test
    void complexTypeLinks() {
        assertTrue(hasEdge("complexType:PurchaseOrderType", "complexType:USAddress", "child shipTo"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "complexType:USAddress", "child billTo"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "element:comment", "child ref"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "complexType:Items", "child items"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "builtin:date", "attribute orderDate"));
        assertTrue(hasEdge("complexType:PurchaseOrderType", "attributeGroup:AuditAttributes", "attributeGroup"));
        assertTrue(hasEdge("complexType:InternationalAddress", "complexType:USAddress", "extends"));
    }

    @Test
    void nestedAnonymousTypesAreAttributedToTheGlobalOwner() {
        assertTrue(hasEdge("complexType:Items", "builtin:string", "child productName"));
        assertTrue(hasEdge("complexType:Items", "builtin:positiveInteger", "restricts"));
        assertTrue(hasEdge("complexType:Items", "simpleType:SKU", "attribute partNum"));
        assertTrue(hasEdge("complexType:Items", "group:ItemExtras", "group"));
        assertTrue(hasEdge("complexType:Items", "element:comment", "child ref"));
    }

    @Test
    void groupAndAttributeGroupLinks() {
        assertTrue(hasEdge("group:ItemExtras", "simpleType:Message", "child giftMessage"));
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
        assertTrue(hasEdge("complexType:Category", "complexType:Category", "child subCategory"));
    }

    @Test
    void undeclaredReferencesBecomeExternalNodes() {
        Model.Node ext = model.nodes.get("type:Label");
        assertEquals("external", ext.kind());
        assertEquals("Label", ext.name());
        assertTrue(hasEdge("complexType:Category", "type:Label", "attribute label"));
    }

    @Test
    void builtinTypesAreSharedNodes() {
        assertEquals("builtin", model.nodes.get("builtin:string").kind());
        assertEquals(1, model.nodes.values().stream().filter(n -> n.name().equals("string")).count());
    }

    @Test
    void lineNumbersPointAtTheStartTag() {
        assertEquals(15, model.nodes.get("element:purchaseOrder").line());
        assertEquals(25, model.nodes.get("complexType:PurchaseOrderType").line());
        // start tag spread over two lines: the line of '<', not of the name attribute
        assertEquals(99, model.nodes.get("simpleType:LimitedText").line());
        assertEquals(0, model.nodes.get("builtin:string").line());
    }

    @Test
    void documentationIsExtracted() {
        assertEquals("Root element of a purchase order document.", model.nodes.get("element:purchaseOrder").doc());
        assertEquals("Stock Keeping Unit, e.g. 123-AB.", model.nodes.get("simpleType:SKU").doc());
        assertEquals("", model.nodes.get("complexType:USAddress").doc());
    }

    @Test
    void jsonIsWellFormed() {
        String json = model.toJson();
        assertTrue(json.startsWith("{\"targetNamespace\":\"http://example.com/po\""));
        assertTrue(json.contains("\"id\":\"element:purchaseOrder\""));
        assertTrue(json.contains("\"from\":\"element:urgentComment\",\"to\":\"element:comment\",\"label\":\"substitutes\""));
        assertFalse(json.contains("\n"));
    }

    @Test
    void rejectsNonSchemaXml() {
        Exception e = assertThrows(Exception.class, () -> XsdParser.parse("<root><a/></root>"));
        assertTrue(e.getMessage().contains("not an XML Schema"));
        assertThrows(Exception.class, () -> XsdParser.parse("not xml at all"));
    }

    @Test
    void defaultNamespaceSchemasResolveBuiltins() throws Exception {
        Model m = XsdParser.parse("""
                <schema xmlns="http://www.w3.org/2001/XMLSchema">
                  <element name="a" type="string"/>
                  <element name="b" type="T"/>
                  <complexType name="T"><sequence><element ref="a"/></sequence></complexType>
                </schema>""");
        assertTrue(m.edges.contains(new Model.Edge("element:a", "builtin:string", "type")));
        assertTrue(m.edges.contains(new Model.Edge("element:b", "complexType:T", "type")));
        assertTrue(m.edges.contains(new Model.Edge("complexType:T", "element:a", "child ref")));
    }
}
