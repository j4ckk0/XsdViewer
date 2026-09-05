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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.schema.SchematronValidator;
import org.jtools.xsdviewer.schema.Severity;
import org.xml.sax.SAXParseException;

/**
 * {@code POST /api/validate?schema=<xsd path>&schematron=<sch path>&phase=<id>}, body = an XML
 * document: validates it against the XSD (the JDK validator, imports resolved next to the file)
 * and / or the Schematron ({@link SchematronValidator}, the given phase or the schema's default) —
 * files the server has already served, never arbitrary paths. Answers {@code {valid, problems:
 * [{source, severity, line, column, message, location?, assertion?, rule?, pattern?, test?}],
 * truncated, phases?, phase?, checked?}}, or 400 when a schema cannot be compiled.
 */
final class ValidateHandler implements HttpHandler {

    /** The {@code source} of a problem: which validation found it. */
    static final String SOURCE_XSD = "xsd", SOURCE_SCHEMATRON = "schematron";
    private static final int INITIAL_CAPACITY = 2048;

    private final ServedSchemaFiles files;

    ValidateHandler(ServedSchemaFiles files) {
        this.files = files;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        QueryString q = QueryString.of(ex);
        for (String p : new String[] { q.get(ApiPath.PARAM_SCHEMA), q.get(ApiPath.PARAM_SCHEMATRON) }) {
            if (!p.isEmpty() && served(p) == null) {
                HttpResponses.error(ex, HttpStatus.NOT_FOUND, Messages.get(MessageKey.FILE_NOT_FOUND, p));
                return;
            }
        }
        Path xsd = served(q.get(ApiPath.PARAM_SCHEMA));
        Path sch = served(q.get(ApiPath.PARAM_SCHEMATRON));
        if (xsd == null && sch == null) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.SCHEMA_EXPECTED));
            return;
        }
        String xml = HttpResponses.readBody(ex);
        XmlValidator.Result xsdResult = null;
        SchematronValidator.Result schResult = null;
        try {
            if (xsd != null) xsdResult = XmlValidator.validate(xsd, xml);
            if (sch != null) {
                try {
                    schResult = SchematronValidator.validate(sch, xml, q.get(ApiPath.PARAM_PHASE));
                } catch (SAXParseException e) {
                    // not well-formed: one problem at the place, as the XSD validator reports it
                    schResult = new SchematronValidator.Result(false, List.of(new SchematronValidator.Problem(
                            Severity.ERROR, e.getLineNumber(), e.getColumnNumber(), e.getMessage(), "", "", "", "", "")), false, List.of(), "", 0);
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {   // the XSD cannot be compiled, the Schematron is not one or not XML, the phase is unknown, an XPath engine failure...
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.SCHEMA_NOT_COMPILED, e.getMessage() == null ? e.toString() : e.getMessage()));
            return;
        }
        boolean valid = (xsdResult == null || xsdResult.valid()) && (schResult == null || schResult.valid());
        boolean truncated = (xsdResult != null && xsdResult.truncated()) || (schResult != null && schResult.truncated());
        JsonWriter w = new JsonWriter(INITIAL_CAPACITY).beginObject().property(JsonKey.VALID, valid);
        w.name(JsonKey.PROBLEMS).beginArray();
        if (xsdResult != null) {
            for (XmlValidator.Problem p : xsdResult.problems()) {
                w.beginObject().property(JsonKey.SOURCE, SOURCE_XSD).property(JsonKey.SEVERITY, p.severity()).property(JsonKey.LINE, p.line())
                        .property(JsonKey.COLUMN, p.column()).property(JsonKey.MESSAGE, p.message()).endObject();
            }
        }
        if (schResult != null) {
            for (SchematronValidator.Problem p : schResult.problems()) {
                w.beginObject().property(JsonKey.SOURCE, SOURCE_SCHEMATRON).property(JsonKey.SEVERITY, p.severity()).property(JsonKey.LINE, p.line())
                        .property(JsonKey.COLUMN, p.column()).property(JsonKey.MESSAGE, p.message());
                optional(w, JsonKey.LOCATION, p.location());
                optional(w, JsonKey.ASSERTION, p.assertion());
                optional(w, JsonKey.RULE, p.rule());
                optional(w, JsonKey.PATTERN, p.pattern());
                optional(w, JsonKey.TEST, p.test());
                w.endObject();
            }
        }
        w.endArray().property(JsonKey.TRUNCATED, truncated);
        if (schResult != null) {
            w.name(JsonKey.PHASES).beginArray();
            for (String phase : schResult.phases()) w.value(phase);
            w.endArray().property(JsonKey.PHASE, schResult.phase()).property(JsonKey.CHECKED, schResult.checked());
        }
        HttpResponses.json(ex, HttpStatus.OK, w.endObject().toString());
    }

    private static void optional(JsonWriter w, String key, String value) {
        if (value != null && !value.isEmpty()) w.property(key, value);
    }

    /** The served file a query parameter names, or null when the parameter is empty or the file is not one the server served. */
    private Path served(String param) {
        if (param.isEmpty()) return null;
        Path path = Path.of(param).toAbsolutePath().normalize();
        return files.contains(path) && Files.isRegularFile(path) ? path : null;
    }
}
