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
import java.util.Set;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.compare.ModelDiff;
import org.jtools.xsdviewer.compare.SchemaDiff;
import org.jtools.xsdviewer.compare.SchemaDiff.Link;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.model.Box;
import org.jtools.xsdviewer.model.ContentTree;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.jtools.xsdviewer.server.RequestFiles.Side;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * {@code POST /api/compare/declarations}, body {@code {"left": side, "right": side}} where a side is
 * {@code {"files": [{"name", "text"}...], "home": n, "id": "..."}}: the two declarations compared.
 * Answers {@code {"left": tree, "right": tree, "counts": {same, changed, removed, added}, "links":
 * {"onlyLeft": [link...], "onlyRight": [link...]}}} — the two content model trees, whole and every box
 * marked ({@link ModelDiff}); a tree is null when its side declares nothing; and, for the graph view,
 * the links of each declaration's neighbourhood the other side does not have ({@link SchemaDiff}),
 * each as its word, the other end's kind and name, its occurrences.
 */
final class CompareDeclarationsHandler implements HttpHandler {

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
        Side left = RequestFiles.side(JsonReader.asObject(request.get(JsonKey.LEFT)));
        Side right = RequestFiles.side(JsonReader.asObject(request.get(JsonKey.RIGHT)));
        Box l = tree(left), r = tree(right);
        ModelDiff.Counts counts = ModelDiff.mark(l, r);
        Map<String, Link> ln = neighbourhood(left), rn = neighbourhood(right);
        JsonWriter w = new JsonWriter().beginObject();
        w.name(JsonKey.LEFT);
        write(w, l);
        w.name(JsonKey.RIGHT);
        write(w, r);
        w.name(JsonKey.COUNTS);
        counts.write(w);
        w.name(JsonKey.LINKS).beginObject();
        w.name(JsonKey.ONLY_LEFT).beginArray();
        for (Map.Entry<String, Link> e : ln.entrySet()) if (!rn.containsKey(e.getKey())) write(w, e.getValue());
        w.endArray().name(JsonKey.ONLY_RIGHT).beginArray();
        for (Map.Entry<String, Link> e : rn.entrySet()) if (!ln.containsKey(e.getKey())) write(w, e.getValue());
        w.endArray().endObject().endObject();
        HttpResponses.json(ex, HttpStatus.OK, w.toString());
    }

    /** The whole tree of a side's declaration, or null when the side has none. */
    private static Box tree(Side side) {
        Node root = side.home() != null ? side.home().node(side.id()) : null;
        return root == null ? null : ContentTree.build(root, side.home(), side.library(), Set.of(), true);
    }

    private static Map<String, Link> neighbourhood(Side side) {
        return side.home() != null && side.home().node(side.id()) != null ? SchemaDiff.neighbourhood(side.home(), side.id()) : Map.of();
    }

    private static void write(JsonWriter w, Box tree) {
        if (tree == null) w.nullValue();
        else tree.write(w);
    }

    private static void write(JsonWriter w, Link link) {
        w.beginObject().property(JsonKey.LABEL, link.label()).property(JsonKey.KIND, link.kind()).property(JsonKey.NAME, link.name());
        if (link.cardinality() != null) w.property(JsonKey.MIN, link.cardinality().min()).property(JsonKey.MAX, link.cardinality().max());
        w.endObject();
    }
}
