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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WsdlParserTest {

    private static SchemaGraph model;

    @BeforeAll
    static void parseSample() throws Exception {
        model = SchemaParser.parse(Files.readString(Path.of("samples/wsdl/purchaseOrderService.wsdl")));
    }

    private static boolean hasEdge(String from, String to, String label) {
        return model.edges.stream().anyMatch(e -> e.from().equals(from) && e.to().equals(to) && e.label().equals(label));
    }

    private static SchemaGraph.Node node(String id) {
        SchemaGraph.Node n = model.nodes.get(id);
        assertTrue(n != null, id + " declared");
        return n;
    }

    @Test
    void declarationsBecomeNodes() {
        Set<String> declared = model.nodes.values().stream()
                .filter(n -> !n.kind().equals(NodeKind.BUILTIN) && !n.kind().equals(NodeKind.EXTERNAL))
                .map(SchemaGraph.Node::id).collect(Collectors.toSet());
        assertEquals(Set.of(
                "service:PurchaseOrderService", "portType:PurchaseOrderPortType", "binding:PurchaseOrderSoapBinding",
                "operation:PurchaseOrderPortType.submitPurchaseOrder", "operation:PurchaseOrderPortType.getOrderStatus",
                "message:SubmitPurchaseOrderRequest", "message:SubmitPurchaseOrderResponse", "message:OrderFault",
                "message:GetOrderStatusRequest", "message:GetOrderStatusResponse",
                "element:submitPurchaseOrderResponse", "element:orderFault"), declared);
        assertEquals("submitPurchaseOrder", node("operation:PurchaseOrderPortType.submitPurchaseOrder").name());
        assertEquals(java.util.List.of("order"), node("message:SubmitPurchaseOrderRequest").members(), "a message's parts are its members");
        assertEquals(NodeKind.ELEMENT, node("element:orderFault").kind());
    }

    @Test
    void headerImportsAndDocumentation() {
        assertEquals("http://example.com/po/service", model.targetNamespace);
        assertEquals("http://example.com/po/service", node("service:PurchaseOrderService").ns());
        assertEquals(1, model.imports.size());
        SchemaGraph.Import i = model.imports.get(0);
        assertEquals(XsdVocabulary.IMPORT, i.tag());
        assertEquals("http://example.com/po", i.namespace());
        assertEquals("../purchaseOrder.xsd", i.schemaLocation());
        assertEquals("The purchase order service, over SOAP 1.1.", node("service:PurchaseOrderService").doc());
        assertEquals("Files a purchase order; answers its identifier.", node("operation:PurchaseOrderPortType.submitPurchaseOrder").doc());
        assertEquals("", node("message:OrderFault").doc());
    }

    @Test
    void chainFromServiceToElements() {
        assertTrue(hasEdge("service:PurchaseOrderService", "portType:PurchaseOrderPortType", "PurchaseOrderPort"), "service -> portType through the binding, labelled with the port");
        assertTrue(hasEdge("binding:PurchaseOrderSoapBinding", "portType:PurchaseOrderPortType", LinkLabel.BINDS));
        assertTrue(hasEdge("portType:PurchaseOrderPortType", "operation:PurchaseOrderPortType.submitPurchaseOrder", LinkLabel.OPERATION));
        assertTrue(hasEdge("portType:PurchaseOrderPortType", "operation:PurchaseOrderPortType.getOrderStatus", LinkLabel.OPERATION));
        assertTrue(hasEdge("operation:PurchaseOrderPortType.submitPurchaseOrder", "message:SubmitPurchaseOrderRequest", LinkLabel.INPUT));
        assertTrue(hasEdge("operation:PurchaseOrderPortType.submitPurchaseOrder", "message:SubmitPurchaseOrderResponse", LinkLabel.OUTPUT));
        assertTrue(hasEdge("operation:PurchaseOrderPortType.submitPurchaseOrder", "message:OrderFault", LinkLabel.FAULT));
        // a part's element declared inline, one declared in the imported schema (external placeholder in its namespace), a part's type
        assertTrue(hasEdge("message:SubmitPurchaseOrderResponse", "element:submitPurchaseOrderResponse", "response"));
        assertTrue(hasEdge("message:SubmitPurchaseOrderRequest", "element:purchaseOrder", "order"));
        SchemaGraph.Node external = node("element:purchaseOrder");
        assertEquals(NodeKind.EXTERNAL, external.kind());
        assertEquals("http://example.com/po", external.ns());
        assertTrue(hasEdge("message:GetOrderStatusRequest", "type:Identifier", "orderId"));
        assertEquals(NodeKind.EXTERNAL, node("type:Identifier").kind());
        assertTrue(hasEdge("message:GetOrderStatusResponse", "builtin:string", "status"));
        // the inline schema's own links
        assertTrue(hasEdge("element:submitPurchaseOrderResponse", "type:Identifier", "orderId"));
        assertTrue(hasEdge("element:orderFault", "builtin:string", LinkLabel.TYPE));
    }

    @Test
    void lineNumbers() throws Exception {
        String[] lines = Files.readString(Path.of("samples/wsdl/purchaseOrderService.wsdl")).split("\n");
        assertTrue(lines[node("service:PurchaseOrderService").line() - 1].contains("<wsdl:service name=\"PurchaseOrderService\""));
        assertTrue(lines[node("operation:PurchaseOrderPortType.getOrderStatus").line() - 1].contains("<wsdl:operation name=\"getOrderStatus\""));
        assertTrue(lines[node("element:orderFault").line() - 1].contains("<xs:element name=\"orderFault\""));
        assertTrue(lines[node("message:OrderFault").line() - 1].contains("<wsdl:message name=\"OrderFault\""));
    }

    @Test
    void serviceWithoutItsBindingLinksToTheBinding() throws Exception {
        SchemaGraph m = SchemaParser.parse("""
                <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:other="urn:other" targetNamespace="urn:s">
                  <wsdl:import namespace="urn:other" location="other.wsdl"/>
                  <wsdl:service name="S"><wsdl:port name="P" binding="other:B"/></wsdl:service>
                </wsdl:definitions>""");
        assertTrue(m.edges.stream().anyMatch(e -> e.from().equals("service:S") && e.to().equals("binding:B") && e.label().equals("P")));
        assertEquals(NodeKind.EXTERNAL, m.nodes.get("binding:B").kind());
        assertEquals("urn:other", m.nodes.get("binding:B").ns());
        assertEquals(WsdlVocabulary.IMPORT, m.imports.get(0).tag());
        assertEquals("other.wsdl", m.imports.get(0).schemaLocation());
    }
}
