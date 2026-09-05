package org.jtools.xsdviewer.examples;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The examples run against the samples, as their usage lines say: what they print is what a developer will see. */
class ExamplesTest {

    private static String output(Runner r) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            r.run(out);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface Runner {
        void run(PrintStream out) throws Exception;
    }

    @Test
    void readSchema() throws Exception {
        String out = output(o -> ReadSchema.run(Path.of("samples/purchaseOrder.xsd"), o));
        assertTrue(out.contains("PurchaseOrderType") && out.contains("lines "), out);
        assertTrue(out.contains("--shipTo 1--> complexType:USAddress"), out);
        assertTrue(out.contains("as JSON"), out);
    }

    @Test
    void readSomethingElse() throws Exception {
        String out = output(o -> ReadSchema.run(Path.of("samples/purchaseOrder.xml"), o));
        assertTrue(out.startsWith("not a schema:"), out);
    }

    @Test
    void modelOfDeclaration() throws Exception {
        String out = output(o -> ModelOfDeclaration.run("complexType:PurchaseOrderType", List.of(Path.of("samples/purchaseOrder.xsd"), Path.of("samples/ext.xsd")), o));
        assertTrue(out.startsWith("complexType PurchaseOrderType"), out);
        assertTrue(out.contains("element shipTo : USAddress"), out);
        assertTrue(out.contains("@attribute country : NMTOKEN [0..1]"), "the named type opened in place, with its attribute: " + out);
    }

    @Test
    void compareDeclarations() throws Exception {
        String out = output(o -> CompareDeclarations.run("complexType:ProductType", Path.of("samples/compare/v1"), Path.of("samples/compare/v2"), o));
        assertTrue(out.startsWith("complexType:ProductType: 6 same, 3 changed, 1 only in samples/compare/v1, 3 only in samples/compare/v2"), out);
        assertTrue(out.lines().anyMatch(l -> l.startsWith("- ") && l.contains("element legacyCode : string")), out);
        assertTrue(out.lines().anyMatch(l -> l.startsWith("+ ") && l.contains("element weight : Weight")), out);
    }

    @Test
    void validateDocument() throws Exception {
        String out = output(o -> ValidateDocument.run(Path.of("samples/purchaseOrder.xml"), Path.of("samples/purchaseOrder.xsd"), Path.of("samples/schematron/purchaseOrder.sch"), o));
        assertTrue(out.contains("purchaseOrder.xsd: valid"), out);
        assertTrue(out.contains("purchaseOrder.sch:"), out);
    }
}
