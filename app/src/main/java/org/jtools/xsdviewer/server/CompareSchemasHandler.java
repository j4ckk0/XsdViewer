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
import java.util.Map;

import org.jtools.xsdviewer.compare.CompareJsonWriter;
import org.jtools.xsdviewer.compare.SchemaDiff;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.schema.SchemaException;
import org.jtools.xsdviewer.schema.SchemaGraph;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * {@code POST /api/compare/schemas}, body {@code {"left": text, "right": text}}: what the two schemas
 * declare and link that the other does not ({@link SchemaDiff}). Answers {@code {"schemas": true,
 * "same": bool, "nodesOnlyLeft", "nodesOnlyRight", "edgesOnlyLeft", "edgesOnlyRight"}}, or
 * {@code {"schemas": false}} when either text is not a schema — the comparison then has only the
 * lines to say.
 */
final class CompareSchemasHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Map<String, Object> request = JsonRequest.of(ex);
        if (request == null) return;
        SchemaGraph left, right;
        try {
            left = ParsedSchemas.of(RequestBody.text(request, JsonKey.LEFT));
            right = ParsedSchemas.of(RequestBody.text(request, JsonKey.RIGHT));
        } catch (SchemaException e) {
            HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.SCHEMAS, false));
            return;
        }
        JsonWriter w = new JsonWriter();
        CompareJsonWriter.schemas(w, SchemaDiff.of(left, right));
        HttpResponses.json(ex, HttpStatus.OK, w.toString());
    }
}
