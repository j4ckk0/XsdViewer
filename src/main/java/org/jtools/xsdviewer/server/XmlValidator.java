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

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.jtools.xsdviewer.schema.Severity;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Validates an XML document against a schema file with the JDK's validator: the schema is compiled
 * from its file (its imports and includes resolve next to it; only files, never the network), the
 * document's problems are collected with their line and column rather than stopping at the first.
 */
final class XmlValidator {

    /** Problems beyond this many are not collected: enough to see what is wrong. */
    static final int MAX_PROBLEMS = 200;
    private static final String FILE_ACCESS = "file";

    /** One problem of the document: {@code error} or {@code warning}, where, what. */
    record Problem(String severity, int line, int column, String message) {}

    /** The outcome: valid when no error was found (warnings do not count), and the problems, truncated when too many. */
    record Result(boolean valid, List<Problem> problems, boolean truncated) {}

    private XmlValidator() {}

    /**
     * @throws SAXException when the schema itself cannot be compiled (its message says why)
     * @throws IOException  when a file cannot be read
     */
    static Result validate(Path schemaFile, String xml) throws SAXException, IOException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, FILE_ACCESS);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        Schema schema = factory.newSchema(schemaFile.toFile());

        Validator validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, FILE_ACCESS);
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        List<Problem> problems = new ArrayList<>();
        boolean[] truncated = { false };
        boolean[] failed = { false };
        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) { add(Severity.WARNING, e); }

            @Override
            public void error(SAXParseException e) { failed[0] = true; add(Severity.ERROR, e); }

            @Override
            public void fatalError(SAXParseException e) throws SAXException { failed[0] = true; add(Severity.ERROR, e); throw e; }

            private void add(String severity, SAXParseException e) {
                if (problems.size() >= MAX_PROBLEMS) { truncated[0] = true; return; }
                problems.add(new Problem(severity, e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
            }
        });
        try {
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            // a fatal error (not well-formed): already collected, the document cannot be read further
            failed[0] = true;
        }
        return new Result(!failed[0], problems, truncated[0]);
    }
}
