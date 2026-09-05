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

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jtools.xsdviewer.schema.SchemaException;
import org.jtools.xsdviewer.schema.SchematronValidator;
import org.jtools.xsdviewer.schema.XmlValidator;

/**
 * A document checked against an XML Schema and a Schematron, as Help ▸ Validate does: the problems of
 * each, located in the document.
 *
 * <pre>java -cp … org.jtools.xsdviewer.examples.ValidateDocument samples/purchaseOrder.xml samples/purchaseOrder.xsd samples/schematron/purchaseOrder.sch</pre>
 */
public final class ValidateDocument {

    private ValidateDocument() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: ValidateDocument <document.xml> <schema.xsd> <rules.sch>");
            System.exit(2);
        }
        run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), System.out);
    }

    static void run(Path document, Path schema, Path schematron, PrintStream out) throws Exception {
        String xml = Files.readString(document);
        try {
            XmlValidator.Result xsd = XmlValidator.validate(schema, xml);   // the JDK's validator: the schema's imports resolve next to it
            out.println(schema.getFileName() + ": " + (xsd.valid() ? "valid" : xsd.problems().size() + " problem(s)"));
            for (XmlValidator.Problem p : xsd.problems()) out.printf("  %s line %d: %s%n", p.severity(), p.line(), p.message());

            // null: the schema's default phase; a phase id, or SchematronValidator.ALL_PHASES, otherwise
            SchematronValidator.Result sch = SchematronValidator.validate(schematron, xml, null);
            out.println(schematron.getFileName() + ": " + (sch.valid() ? "valid" : sch.problems().size() + " report(s)"));
            for (SchematronValidator.Problem p : sch.problems()) out.printf("  %s line %d: %s%n", p.severity(), p.line(), p.message());
        } catch (SchemaException e) {
            out.println("cannot validate: " + e.getMessage());   // the schema does not compile, the Schematron is not one, the phase is unknown
        }
    }
}
