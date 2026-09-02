package org.jtools.xsdviewer.server;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

/** Against samples/import/order.xsd, whose imports and includes (address, items, types) must resolve next to it. */
class XmlValidatorTest {

    private static final Path ORDER = Path.of("samples/import/order.xsd");
    private static final String VALID_ORDER = """
            <ord:order xmlns:ord="http://example.com/order" xmlns:adr="http://example.com/address" id="ORD-000123">
              <ord:shipTo><adr:street>1 Main St</adr:street><adr:city>Springfield</adr:city><adr:country>US</adr:country></ord:shipTo>
              <ord:billTo><adr:street>1 Main St</adr:street><adr:city>Springfield</adr:city><adr:country>US</adr:country></ord:billTo>
              <ord:items><ord:item><ord:productName>Lawnmower</ord:productName><ord:quantity>1</ord:quantity></ord:item></ord:items>
            </ord:order>""";

    @Test
    void aValidDocument() throws Exception {
        XmlValidator.Result r = XmlValidator.validate(ORDER, VALID_ORDER);
        assertTrue(r.valid(), r.problems().toString());
        assertTrue(r.problems().isEmpty());
        assertFalse(r.truncated());
    }

    @Test
    void problemsAreLocated() throws Exception {
        String bad = VALID_ORDER.replace("ORD-000123", "123").replace("<ord:quantity>1</ord:quantity>", "<ord:quantity>1000</ord:quantity>");
        XmlValidator.Result r = XmlValidator.validate(ORDER, bad);
        assertFalse(r.valid());
        // each bad value is reported twice (the facet, then the type): two lines are wrong
        assertTrue(r.problems().stream().allMatch(p -> p.severity().equals("error") && p.line() > 0 && p.column() > 0), r.problems().toString());
        assertEquals(java.util.Set.of(1, 4), r.problems().stream().map(XmlValidator.Problem::line).collect(java.util.stream.Collectors.toSet()), "the id on the first line, the quantity on the fourth");
    }

    @Test
    void aDocumentThatIsNotWellFormed() throws Exception {
        XmlValidator.Result r = XmlValidator.validate(ORDER, "<ord:order xmlns:ord=\"http://example.com/order\">");
        assertFalse(r.valid());
        assertFalse(r.problems().isEmpty());
        assertEquals(1, r.problems().get(0).line());
    }

    @Test
    void aBrokenSchemaIsRefused(@TempDir Path dir) throws Exception {
        Path broken = dir.resolve("broken.xsd");
        Files.writeString(broken, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"a\" type=\"nope\"/></xs:schema>");
        assertThrows(SAXException.class, () -> XmlValidator.validate(broken, "<a/>"));
    }
}
