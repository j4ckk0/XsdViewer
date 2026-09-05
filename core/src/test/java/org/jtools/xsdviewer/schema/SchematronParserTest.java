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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SchematronParserTest {

    private static final Path SAMPLE = Path.of("samples/schematron/purchaseOrder.sch");
    private static SchemaGraph model;

    @BeforeAll
    static void parseSample() throws Exception {
        model = SchemaParser.parse(Files.readString(SAMPLE));
    }

    private static boolean hasEdge(SchemaGraph g, String from, String to, String label) {
        return g.edges.stream().anyMatch(e -> e.from().equals(from) && e.to().equals(to) && e.label().equals(label));
    }

    private static SchemaGraph.Node node(String id) {
        SchemaGraph.Node n = model.nodes.get(id);
        assertTrue(n != null, id + " declared");
        return n;
    }

    @Test
    void declarationsBecomeNodes() {
        Set<String> declared = model.nodes.values().stream()
                .filter(n -> !n.kind().equals(NodeKind.EXTERNAL))
                .map(SchemaGraph.Node::id).collect(Collectors.toSet());
        assertEquals(Set.of(
                "phase:basic", "phase:full",
                "pattern:structure", "pattern:dates", "pattern:amounts", "pattern:nonEmpty", "pattern:namesNonEmpty", "pattern:Addresses",
                "rule:structure/po:purchaseOrder", "rule:structure/po:item", "rule:hasComment", "rule:dates/po:item[po:shipDate]",
                "rule:amounts/po:USPrice", "rule:nonEmpty/$element", "rule:Addresses/po:shipTo | po:billTo",
                "assert:PO-001", "assert:structure/po:purchaseOrder/count(po:items/po:item) > 0", "assert:structure/po:item/po:quantity < 100",
                "report:hasComment/po:USPrice > 1000 and not(po:comment)",
                "assert:dates/po:item[po:shipDate]/translate(po:shipDate, '-', '') >= translate(../../@orderDate, '-', '')",
                "assert:amounts/po:USPrice/. >= 0", "report:amounts/po:USPrice/. = 0",
                "assert:nonEmpty/$element/normalize-space(.) != ''",
                "assert:Addresses/po:shipTo | po:billTo/string-length(po:zip) = 5",
                "diagnostic:priceTooHigh"), declared);
        assertTrue(model.nodes.values().stream().noneMatch(n -> n.kind().equals(NodeKind.EXTERNAL)), "everything referenced is declared");
        assertEquals("", model.targetNamespace);
        assertTrue(model.imports.isEmpty());
    }

    @Test
    void namesAndExpressions() {
        // a rule is named by its context, an assertion by its id else its test; the expression is kept whole apart
        SchemaGraph.Node rule = node("rule:structure/po:purchaseOrder");
        assertEquals(NodeKind.RULE, rule.kind());
        assertEquals("po:purchaseOrder", rule.name());
        assertEquals("po:purchaseOrder", rule.xpath());
        assertEquals("", rule.ns());
        SchemaGraph.Node named = node("assert:PO-001");
        assertEquals("PO-001", named.name());
        assertEquals("po:shipTo and po:billTo", named.xpath());
        SchemaGraph.Node unnamed = node("assert:structure/po:item/po:quantity < 100");
        assertEquals("po:quantity < 100", unnamed.name());
        assertEquals("po:quantity < 100", unnamed.xpath());
        // an abstract rule has no context: its id names it
        SchemaGraph.Node abstractRule = node("rule:hasComment");
        assertEquals("hasComment", abstractRule.name());
        assertEquals("", abstractRule.xpath());
        // a pattern without an id takes its title
        assertEquals("Addresses", node("pattern:Addresses").name());
        assertEquals("", node("phase:basic").xpath());
    }

    @Test
    void documentation() {
        assertEquals("The structural checks only: what a quick import needs.", node("phase:basic").doc());
        assertEquals("Structure of an order", node("pattern:structure").doc());
        assertEquals("Dates\nThe dates of an order must agree with each other.", node("pattern:dates").doc());
        assertEquals("", node("pattern:Addresses").doc(), "the title names the pattern, it is not repeated");
        // messages: the role or flag first, a value-of and a name shown as placeholders
        assertEquals("[error] An order names both a shipping and a billing address.", node("assert:PO-001").doc());
        assertEquals("At most 99 of {po:productName} per line.", node("assert:structure/po:item/po:quantity < 100").doc());
        assertEquals("[fatal] An item cannot ship before its order date.", node("assert:dates/po:item[po:shipDate]/translate(po:shipDate, '-', '') >= translate(../../@orderDate, '-', '')").doc());
        assertEquals("A price is never negative ({name()}).", node("assert:amounts/po:USPrice/. >= 0").doc());
        assertEquals("The item {po:productName} costs {po:USPrice}.", node("diagnostic:priceTooHigh").doc());
    }

    @Test
    void chainFromPhaseToDiagnostic() {
        assertTrue(hasEdge(model, "phase:basic", "pattern:structure", LinkLabel.ACTIVE));
        assertFalse(hasEdge(model, "phase:basic", "pattern:dates", LinkLabel.ACTIVE));
        assertTrue(hasEdge(model, "phase:full", "pattern:dates", LinkLabel.ACTIVE));
        assertTrue(hasEdge(model, "pattern:structure", "rule:structure/po:purchaseOrder", LinkLabel.RULE));
        assertTrue(hasEdge(model, "pattern:structure", "rule:hasComment", LinkLabel.RULE));
        assertTrue(hasEdge(model, "rule:structure/po:purchaseOrder", "assert:PO-001", LinkLabel.ASSERT));
        assertTrue(hasEdge(model, "rule:amounts/po:USPrice", "report:amounts/po:USPrice/. = 0", LinkLabel.REPORT));
        assertTrue(hasEdge(model, "rule:structure/po:item", "rule:hasComment", LinkLabel.EXTENDS));
        assertTrue(hasEdge(model, "pattern:namesNonEmpty", "pattern:nonEmpty", LinkLabel.IS_A));
        assertTrue(hasEdge(model, "report:hasComment/po:USPrice > 1000 and not(po:comment)", "diagnostic:priceTooHigh", LinkLabel.DIAGNOSTIC));
        assertEquals(24, model.edges.size());   // 9 assertions, 7 rules, 5 active, extends, is a, diagnostic
    }

    @Test
    void lineNumbers() throws Exception {
        String[] lines = Files.readString(SAMPLE).split("\n");
        assertTrue(lines[node("phase:full").line() - 1].contains("<sch:phase id=\"full\">"));
        assertTrue(lines[node("pattern:Addresses").line() - 1].equals("  <sch:pattern>"));
        assertTrue(lines[node("rule:structure/po:item").line() - 1].contains("<sch:rule context=\"po:item\">"));
        assertTrue(lines[node("assert:PO-001").line() - 1].contains("id=\"PO-001\""));
        assertTrue(lines[node("diagnostic:priceTooHigh").line() - 1].contains("<sch:diagnostic id=\"priceTooHigh\">"));
        assertTrue(model.nodes.values().stream().allMatch(n -> n.line() > 0), "every declaration has its line");
    }

    @Test
    void referencesToOtherFilesAndDuplicates() throws Exception {
        SchemaGraph m = SchemaParser.parse("""
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                  <include href="common.sch"/>
                  <phase id="p"><active pattern="elsewhere"/></phase>
                  <pattern>
                    <rule context="a"><extends rule="shared"/><assert test="b" diagnostics="d1 d2">m</assert></rule>
                    <rule context="a"><assert test="b">again</assert></rule>
                  </pattern>
                  <pattern><rule context="c"><assert test="d">m</assert></rule></pattern>
                </schema>""");
        assertEquals(List.of(new SchemaGraph.Import(SchematronNames.INCLUDE, "", "common.sch")), m.imports);
        assertEquals(NodeKind.EXTERNAL, m.nodes.get("pattern:elsewhere").kind());
        assertEquals(NodeKind.EXTERNAL, m.nodes.get("rule:shared").kind());
        assertEquals(NodeKind.EXTERNAL, m.nodes.get("diagnostic:d2").kind());
        assertTrue(hasEdge(m, "phase:p", "pattern:elsewhere", LinkLabel.ACTIVE));
        assertTrue(hasEdge(m, "assert:pattern 1/a/b", "diagnostic:d1", LinkLabel.DIAGNOSTIC));
        // unnamed patterns are ranked; a second rule with the same context, a second identical assertion, get a suffix
        assertTrue(m.declares("pattern:pattern 1") && m.declares("pattern:pattern 2"));
        assertEquals("pattern 2", m.nodes.get("pattern:pattern 2").name());
        assertTrue(m.declares("rule:pattern 1/a") && m.declares("rule:pattern 1/a#2"));
        assertEquals("a", m.nodes.get("rule:pattern 1/a#2").name());
        assertTrue(m.declares("assert:pattern 1/a/b") && m.declares("assert:pattern 1/a#2/b"));
        assertTrue(hasEdge(m, "rule:pattern 1/a#2", "assert:pattern 1/a#2/b", LinkLabel.ASSERT));
        assertTrue(hasEdge(m, "pattern:pattern 2", "rule:pattern 2/c", LinkLabel.RULE));
    }

    @Test
    void foreignElementsAreIgnored() throws Exception {
        SchemaGraph m = SchemaParser.parse("""
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" xmlns:h="http://www.w3.org/1999/xhtml">
                  <h:p>documentation in another namespace</h:p>
                  <pattern id="p"><note xmlns=""/><rule context="x"><assert test="y">m</assert></rule></pattern>
                </schema>""");
        assertEquals(Set.of("pattern:p", "rule:p/x", "assert:p/x/y"), m.nodes.keySet());
    }

    @Test
    void schematron15AndFragments() throws Exception {
        SchemaGraph old = SchemaParser.parse("""
                <sch:schema xmlns:sch="http://www.ascc.net/xml/schematron">
                  <sch:pattern name="Old style"><sch:rule context="x"><sch:assert test="y">m</sch:assert></sch:rule></sch:pattern>
                </sch:schema>""");
        assertTrue(old.declares("pattern:Old style"));
        assertTrue(hasEdge(old, "rule:Old style/x", "assert:Old style/x/y", LinkLabel.ASSERT));
        // a file meant for sch:include: its root is a pattern
        SchemaGraph fragment = SchemaParser.parse("""
                <pattern xmlns="http://purl.oclc.org/dsdl/schematron" id="shared">
                  <rule context="x"><report test="y">m</report></rule>
                </pattern>""");
        assertEquals(Set.of("pattern:shared", "rule:shared/x", "report:shared/x/y"), fragment.nodes.keySet());
        assertEquals(2, fragment.nodes.get("rule:shared/x").line());
        SchemaGraph rule = SchemaParser.parse("<rule xmlns=\"http://purl.oclc.org/dsdl/schematron\" context=\"x\"><assert test=\"y\">m</assert></rule>");
        assertEquals(Set.of("rule:x", "assert:x/y"), rule.nodes.keySet());
    }

    @Test
    void jsonCarriesTheExpression() {
        String json = model.toJson();
        assertTrue(json.contains("\"id\":\"assert:PO-001\",\"kind\":\"assert\",\"name\":\"PO-001\",\"ns\":\"\",\"line\":23,\"endLine\":23,\"doc\":\"[error] An order names both a shipping and a billing address.\",\"xpath\":\"po:shipTo and po:billTo\"}"), json);
        assertFalse(json.contains("\"id\":\"phase:basic\",\"kind\":\"phase\",\"name\":\"basic\",\"ns\":\"\",\"line\":8,\"endLine\":11,\"doc\":\"The structural checks only: what a quick import needs.\",\"xpath\""), "no xpath key without an expression");
    }
}
