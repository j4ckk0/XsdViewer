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

import org.jtools.xsdviewer.schema.NodeKind;
import org.jtools.xsdviewer.schema.SchemaException;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.jtools.xsdviewer.schema.SchemaGraph.Edge;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.jtools.xsdviewer.schema.SchemaParser;

/**
 * Reads a schema file — an XSD, a WSDL or a Schematron — and says what it declares and how the
 * declarations refer to one another: what the XsdViewer page shows in its object list and its graph.
 *
 * <pre>java -cp … org.jtools.xsdviewer.examples.ReadSchema samples/purchaseOrder.xsd</pre>
 */
public final class ReadSchema {

    private ReadSchema() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: ReadSchema <file.xsd|file.wsdl|file.sch>");
            System.exit(2);
        }
        run(Path.of(args[0]), System.out);
    }

    static void run(Path file, PrintStream out) throws Exception {
        SchemaGraph graph;
        try {
            graph = SchemaParser.parse(Files.readString(file));   // the root tag says whether it is an XSD, a WSDL or a Schematron
        } catch (SchemaException e) {
            out.println("not a schema: " + e.getMessage());   // in English or French, whatever the JVM speaks
            return;
        }
        out.println("target namespace: " + (graph.targetNamespace.isEmpty() ? "(none)" : graph.targetNamespace));
        out.println();
        out.println("declarations:");
        for (Node n : graph.nodes.values()) {
            // placeholders stand for what the file uses without declaring: built-in types, and objects of other files
            if (NodeKind.BUILTIN.equals(n.kind()) || NodeKind.EXTERNAL.equals(n.kind())) continue;
            out.printf("  %-14s %-28s lines %d-%d%n", n.kind(), n.name(), n.line(), n.endLine());
        }
        out.println();
        out.println("links (from --word [occurrences]--> to):");
        for (Edge e : graph.edges) {
            String card = Cardinality.text(e.cardinality());
            out.printf("  %s --%s%s--> %s%n", e.from(), e.label(), card.isEmpty() ? "" : " " + card, e.to());
        }
        out.println();
        out.println("as JSON, what the page reads: " + graph.toJson().length() + " characters");
    }
}
