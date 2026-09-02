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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.xml.sax.SAXException;

/**
 * {@code POST /api/validate?schema=<path>}, body = an XML document: validates it against that
 * schema file — one the server has already served (a file it read for the page), never an
 * arbitrary path. Answers {@code {valid, problems: [{severity, line, column, message}], truncated}},
 * or 400 when the schema cannot be compiled.
 */
final class ValidateHandler implements HttpHandler {

    private static final int INITIAL_CAPACITY = 2048;

    private final ServedSchemaFiles files;

    ValidateHandler(ServedSchemaFiles files) {
        this.files = files;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        String schema = QueryString.of(ex).get(ApiPath.PARAM_SCHEMA);
        if (schema.isEmpty()) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.SCHEMA_EXPECTED));
            return;
        }
        Path path = Path.of(schema).toAbsolutePath().normalize();
        if (!files.contains(path) || !Files.isRegularFile(path)) {
            HttpResponses.error(ex, HttpStatus.NOT_FOUND, Messages.get(MessageKey.FILE_NOT_FOUND, schema));
            return;
        }
        XmlValidator.Result result;
        try {
            result = XmlValidator.validate(path, HttpResponses.readBody(ex));
        } catch (SAXException e) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.SCHEMA_NOT_COMPILED, e.getMessage()));
            return;
        }
        JsonWriter w = new JsonWriter(INITIAL_CAPACITY).beginObject().property(JsonKey.VALID, result.valid());
        w.name(JsonKey.PROBLEMS).beginArray();
        for (XmlValidator.Problem p : result.problems()) {
            w.beginObject().property(JsonKey.SEVERITY, p.severity()).property(JsonKey.LINE, p.line())
                    .property(JsonKey.COLUMN, p.column()).property(JsonKey.MESSAGE, p.message()).endObject();
        }
        w.endArray().property(JsonKey.TRUNCATED, result.truncated());
        HttpResponses.json(ex, HttpStatus.OK, w.endObject().toString());
    }
}
