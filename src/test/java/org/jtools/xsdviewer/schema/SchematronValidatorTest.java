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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXParseException;

class SchematronValidatorTest {

    private static final Path SCH = Path.of("samples/schematron/purchaseOrder.sch");
    private static final Path XML = Path.of("samples/purchaseOrder.xml");

    private static List<SchematronValidator.Problem> of(SchematronValidator.Result r, String severity) {
        return r.problems().stream().filter(p -> p.severity().equals(severity)).toList();
    }

    @Test
    void theSampleDocumentPassesEveryRule() throws Exception {
        SchematronValidator.Result r = SchematronValidator.validate(SCH, Files.readString(XML), null);
        assertTrue(r.valid(), r.problems().toString());
        assertTrue(r.problems().isEmpty(), r.problems().toString());
        assertEquals("full", r.phase(), "the schema's default phase");
        assertEquals(List.of("basic", "full"), r.phases());
        // 1 order x 2 asserts, 2 items x (the extended report + quantity), 2 shipDates, 2 prices x 2, 2 names; the "Addresses" pattern has no id: no phase activates it
        assertEquals(14, r.checked());
        assertEquals(16, SchematronValidator.validate(SCH, Files.readString(XML), SchematronValidator.ALL_PHASES).checked());
        assertFalse(r.truncated());
    }

    @Test
    void firingAssertionsAreLocatedAndNamed() throws Exception {
        String xml = Files.readString(XML)
                .replace("<po:quantity>1</po:quantity>", "<po:quantity>120</po:quantity>")
                .replace("<po:comment>Confirm this is electric</po:comment>\n", "")
                .replace("<po:shipDate>2026-09-03</po:shipDate>", "<po:shipDate>2026-08-30</po:shipDate>");
        SchematronValidator.Result r = SchematronValidator.validate(SCH, xml, null);
        assertFalse(r.valid());
        assertEquals(3, r.problems().size(), r.problems().toString());
        // the report of the abstract rule the item's rule extends comes first: the extends sits before the assert
        SchematronValidator.Problem expensive = r.problems().get(0);
        assertEquals(Severity.WARNING, expensive.severity());
        assertEquals("report:hasComment/po:USPrice > 1000 and not(po:comment)", expensive.assertion());
        assertEquals("rule:structure/po:item", expensive.rule(), "reported against the rule that fired");
        assertEquals("An expensive item deserves a comment. — The item Lawnmower costs 1480.00.", expensive.message(), "the diagnostic it names follows");
        SchematronValidator.Problem quantity = r.problems().get(1);
        assertEquals(Severity.ERROR, quantity.severity());
        assertEquals("At most 99 of Lawnmower per line.", quantity.message(), "the value-of is filled in");
        assertEquals("/po:purchaseOrder/po:items/po:item[1]", quantity.location());
        assertEquals("assert:structure/po:item/po:quantity < 100", quantity.assertion());
        assertEquals("rule:structure/po:item", quantity.rule());
        assertEquals("pattern:structure", quantity.pattern());
        assertEquals("po:quantity < 100", quantity.test());
        String[] lines = xml.split("\n");
        assertTrue(lines[quantity.line() - 1].contains("<po:item partNum=\"872-AA\">"), "the line of the context node");
        assertTrue(quantity.column() > 0);
        // the date rule: its flag is not a warning
        SchematronValidator.Problem date = r.problems().get(2);
        assertEquals(Severity.ERROR, date.severity());
        assertEquals("pattern:dates", date.pattern());
        assertEquals("An item cannot ship before its order date.", date.message());
    }

    @Test
    void phasesSelectPatterns() throws Exception {
        String xml = Files.readString(XML).replace("<po:shipDate>2026-09-03</po:shipDate>", "<po:shipDate>2026-08-30</po:shipDate>");
        assertFalse(SchematronValidator.validate(SCH, xml, "full").valid());
        SchematronValidator.Result basic = SchematronValidator.validate(SCH, xml, "basic");
        assertTrue(basic.valid(), "the dates pattern is not active in the basic phase");
        assertEquals("basic", basic.phase());
        assertEquals(6, basic.checked());
        assertFalse(SchematronValidator.validate(SCH, xml, SchematronValidator.ALL_PHASES).valid());
        Exception e = assertThrows(IllegalArgumentException.class, () -> SchematronValidator.validate(SCH, xml, "nope"));
        assertTrue(e.getMessage().contains("nope"));
    }

    @Test
    void abstractPatternsAndNames() throws Exception {
        String xml = Files.readString(XML).replace("<po:name>Alice Smith</po:name>", "<po:name>  </po:name>").replace("<po:USPrice>39.98</po:USPrice>", "<po:USPrice>-1</po:USPrice>");
        SchematronValidator.Result r = SchematronValidator.validate(SCH, xml, null);
        List<SchematronValidator.Problem> errors = of(r, Severity.ERROR);
        assertEquals(2, errors.size(), r.problems().toString());
        SchematronValidator.Problem price = errors.get(0);
        assertEquals("A price is never negative (po:USPrice).", price.message(), "a <name/> is the context node's name");
        assertEquals("/po:purchaseOrder/po:items/po:item[2]/po:USPrice", price.location());
        SchematronValidator.Problem name = errors.get(1);
        assertEquals("A po:name is never empty.", name.message());
        assertEquals("pattern:namesNonEmpty", name.pattern(), "the instance pattern");
        assertEquals("assert:nonEmpty/$element/normalize-space(.) != ''", name.assertion(), "the abstract pattern's assertion");
        assertEquals("normalize-space(.) != ''", name.test());
        assertEquals("/po:purchaseOrder/po:shipTo/po:name", name.location());
    }

    @Test
    void unsupportedExpressionsAreReportedNotIgnored(@TempDir Path dir) throws Exception {
        Path sch = dir.resolve("modern.sch");
        Files.writeString(sch, """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="p">
                    <rule context="a">
                      <assert test="xs:date(@d) ge xs:date('2020-01-01')" id="A1">too old</assert>
                      <assert test="@d != ''" id="A2">a date</assert>
                    </rule>
                    <rule context="b[matches(., '\\d')]"><assert test="false()">never</assert></rule>
                  </pattern>
                </schema>""");
        SchematronValidator.Result r = SchematronValidator.validate(sch, "<r><a d=''/><a d='x'/><b>1</b></r>", null);
        List<SchematronValidator.Problem> unsupported = of(r, Severity.UNSUPPORTED);
        assertEquals(2, unsupported.size(), r.problems().toString());
        assertEquals("assert:A1", unsupported.get(0).assertion());
        assertEquals(0, unsupported.get(0).line());
        assertFalse(unsupported.get(0).message().isEmpty(), "the engine's reason");
        assertEquals("rule:p/b[matches(., '\\d')]", unsupported.get(1).rule(), "a context the engine cannot compile: the rule is skipped");
        assertEquals(1, of(r, Severity.ERROR).size(), "the other assertion still runs");
        assertEquals("assert:A2", of(r, Severity.ERROR).get(0).assertion());
        assertEquals(2, r.checked());
        assertFalse(r.valid());
    }

    @Test
    void letsExtendsIncludesAndFirstRuleWins(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("shared.sch"), """
                <pattern xmlns="http://purl.oclc.org/dsdl/schematron" id="shared">
                  <rule context="item"><assert test="@n &lt;= $max">at most <value-of select="$max"/> (<value-of select="@n"/>)</assert></rule>
                </pattern>""");
        Path sch = dir.resolve("main.sch");
        Files.writeString(sch, """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                  <let name="max" value="/order/@max"/>
                  <include href="shared.sch"/>
                  <pattern id="own">
                    <rule abstract="true" id="named"><let name="label" value="concat(name(), ' ', @n)"/><report test="@n = 0">empty <value-of select="$label"/></report></rule>
                    <rule context="item[@n = 0]"><extends rule="named"/></rule>
                    <rule context="item"><assert test="@n &lt; 100">huge</assert></rule>
                  </pattern>
                  <pattern id="gone"><rule context="x"><extends rule="missing"/></rule></pattern>
                  <pattern id="lost"><include href="nowhere.sch"/></pattern>
                </schema>""");
        SchematronValidator.Result r = SchematronValidator.validate(sch, "<order max='5'><item n='0'/><item n='7'/><item n='200'/></order>", null);
        List<String> messages = r.problems().stream().map(SchematronValidator.Problem::message).toList();
        // the included pattern (a schema-level let), the extended abstract rule (its own let), the first matching rule only (n=0 is not "huge"-checked, 200 is)
        assertTrue(messages.contains("at most 5 (7)"), messages.toString());
        assertTrue(messages.contains("at most 5 (200)"), messages.toString());
        assertTrue(messages.contains("empty item 0"), messages.toString());
        assertTrue(messages.contains("huge"), messages.toString());
        assertEquals(1, messages.stream().filter("huge"::equals).count(), "item n=0 fired the first rule of the pattern, not the second");
        assertEquals(2, of(r, Severity.UNSUPPORTED).size(), "a missing abstract rule, a missing include: " + r.problems());
        assertEquals("pattern:shared", r.problems().get(2).pattern());
    }

    @Test
    void everyMissingIncludeAndEmptyContextIsReported(@TempDir Path dir) throws Exception {
        Path sch = dir.resolve("odd.sch");
        Files.writeString(sch, """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                  <include href="nope1.sch"/><include href="nope2.sch"/>
                  <pattern id="p"><rule context=""><assert test="x">m</assert></rule><rule context=""><assert test="x">m</assert></rule></pattern>
                </schema>""");
        SchematronValidator.Result r = SchematronValidator.validate(sch, "<r/>", null);
        assertEquals(3, of(r, Severity.UNSUPPORTED).size(), "two includes, one empty context (the same twice): " + r.problems());
        assertTrue(r.valid());
    }

    @Test
    void aDocumentThatIsNotWellFormedOrNotASchematron(@TempDir Path dir) throws Exception {
        SAXParseException e = assertThrows(SAXParseException.class, () -> SchematronValidator.validate(SCH, "<po:purchaseOrder xmlns:po='http://example.com/po'>", null));
        assertEquals(1, e.getLineNumber());
        Path xsd = dir.resolve("x.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>");
        assertThrows(IllegalArgumentException.class, () -> SchematronValidator.validate(xsd, "<a/>", null));
    }

    /** The contract with the graph: what a problem names is a node of the Schematron as the page parsed it. */
    @Test
    void problemsNameNodesOfTheGraph() throws Exception {
        SchemaGraph graph = SchemaParser.parse(Files.readString(SCH));
        String xml = Files.readString(XML)
                .replace("<po:quantity>1</po:quantity>", "<po:quantity>120</po:quantity>")
                .replace("<po:name>Alice Smith</po:name>", "<po:name> </po:name>")
                .replace("<po:USPrice>39.98</po:USPrice>", "<po:USPrice>0</po:USPrice>");
        SchematronValidator.Result r = SchematronValidator.validate(SCH, xml, SchematronValidator.ALL_PHASES);
        assertEquals(3, r.problems().size(), r.problems().toString());   // the quantity, the free item, the empty name
        for (SchematronValidator.Problem p : r.problems()) {
            for (String id : List.of(p.assertion(), p.rule(), p.pattern())) {
                assertTrue(graph.declares(id), id + " is a node of the graph (" + p + ")");
            }
            assertEquals(p.test(), graph.nodes.get(p.assertion()).xpath(), "the test is the assertion's expression");
        }
    }

    @Test
    void contextsBecomeSelections() {
        assertEquals("//po:item", SchematronValidator.selection("po:item"));
        assertEquals("/", SchematronValidator.selection("/"));
        assertEquals("//po:shipTo | //po:billTo", SchematronValidator.selection("po:shipTo | po:billTo"));
        assertEquals("//a[b | c] | /d", SchematronValidator.selection("a[b | c] | /d"));
        assertEquals("//@id", SchematronValidator.selection("@id"));
    }
}
