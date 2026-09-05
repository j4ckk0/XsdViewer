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
import java.util.List;
import java.util.Map;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.compare.SchemaDiff;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.schema.SchemaException;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaGraph.Edge;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * {@code POST /api/compare/schemas}, body {@code {"left": text, "right": text}}: what the two schemas
 * declare and link that the other does not ({@link SchemaDiff}). Answers {@code {"schemas": true,
 * "same": bool, "nodesOnlyLeft": [{id, kind, name}], "nodesOnlyRight": [...], "edgesOnlyLeft": [{from,
 * to, label, min?, max?}], "edgesOnlyRight": [...]}}, or {@code {"schemas": false}} when either text is
 * not a schema — the comparison then has only the lines to say.
 */
final class CompareSchemasHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        Map<String, Object> request;
        try {
            request = JsonReader.asObject(JsonReader.parse(HttpResponses.readBody(ex)));
        } catch (IllegalArgumentException e) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.INVALID_JSON, e.getMessage()));
            return;
        }
        SchemaGraph left, right;
        try {
            left = RequestFiles.parse(JsonReader.asString(request.get(JsonKey.LEFT)));
            right = RequestFiles.parse(JsonReader.asString(request.get(JsonKey.RIGHT)));
        } catch (SchemaException e) {
            HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.SCHEMAS, false));
            return;
        }
        SchemaDiff.Result d = SchemaDiff.of(left, right);
        JsonWriter w = new JsonWriter().beginObject().property(JsonKey.SCHEMAS, true).property(JsonKey.SAME, d.same());
        nodes(w.name(JsonKey.NODES_ONLY_LEFT), d.nodesOnlyLeft());
        nodes(w.name(JsonKey.NODES_ONLY_RIGHT), d.nodesOnlyRight());
        edges(w.name(JsonKey.EDGES_ONLY_LEFT), d.edgesOnlyLeft());
        edges(w.name(JsonKey.EDGES_ONLY_RIGHT), d.edgesOnlyRight());
        HttpResponses.json(ex, HttpStatus.OK, w.endObject().toString());
    }

    private static void nodes(JsonWriter w, List<Node> nodes) {
        w.beginArray();
        for (Node n : nodes) w.beginObject().property(JsonKey.ID, n.id()).property(JsonKey.KIND, n.kind()).property(JsonKey.NAME, n.name()).endObject();
        w.endArray();
    }

    private static void edges(JsonWriter w, List<Edge> edges) {
        w.beginArray();
        for (Edge e : edges) {
            w.beginObject().property(JsonKey.FROM, e.from()).property(JsonKey.TO, e.to()).property(JsonKey.LABEL, e.label());
            if (e.cardinality() != null) w.property(JsonKey.MIN, e.cardinality().min()).property(JsonKey.MAX, e.cardinality().max());
            w.endObject();
        }
        w.endArray();
    }
}
