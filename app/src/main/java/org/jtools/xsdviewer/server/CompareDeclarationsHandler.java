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

import org.jtools.xsdviewer.compare.CompareJsonWriter;
import org.jtools.xsdviewer.compare.ModelDiff;
import org.jtools.xsdviewer.compare.SchemaDiff;
import org.jtools.xsdviewer.compare.SchemaDiff.Link;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.model.Box;
import org.jtools.xsdviewer.model.BoxJsonWriter;
import org.jtools.xsdviewer.model.ContentTree;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.jtools.xsdviewer.server.RequestBody.Side;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * {@code POST /api/compare/declarations}, body {@code {"left": side, "right": side}} where a side is
 * {@code {"files": [{"name", "text"}...], "home": n, "id": "..."}}: the two declarations compared.
 * Answers {@code {"left": tree, "right": tree, "counts": {same, changed, removed, added}, "links":
 * {"onlyLeft": [link...], "onlyRight": [link...]}}} — the two content model trees, whole and every box
 * marked ({@link ModelDiff}); a tree is null when its side declares nothing; and, for the graph view,
 * the links of each declaration's neighbourhood the other side does not have ({@link SchemaDiff}).
 */
final class CompareDeclarationsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Map<String, Object> request = JsonRequest.of(ex);
        if (request == null) return;
        Side left = RequestBody.side(JsonReader.asObject(request.get(JsonKey.LEFT)));
        Side right = RequestBody.side(JsonReader.asObject(request.get(JsonKey.RIGHT)));
        Box l = tree(left), r = tree(right);
        ModelDiff.Counts counts = ModelDiff.mark(l, r);
        JsonWriter w = new JsonWriter().beginObject();
        BoxJsonWriter.write(w.name(JsonKey.LEFT), l);
        BoxJsonWriter.write(w.name(JsonKey.RIGHT), r);
        CompareJsonWriter.counts(w.name(JsonKey.COUNTS), counts);
        CompareJsonWriter.links(w.name(JsonKey.LINKS), neighbourhood(left), neighbourhood(right));
        HttpResponses.json(ex, HttpStatus.OK, w.endObject().toString());
    }

    /** The whole tree of a side's declaration, or null when the side has none: the comparison then marks the other side wholly. */
    private static Box tree(Side side) {
        Node root = side.home() != null ? side.home().node(side.id()) : null;
        return root == null ? null : ContentTree.build(root, side.home(), side.library(), Set.of(), true);
    }

    private static Map<String, Link> neighbourhood(Side side) {
        return side.home() != null && side.home().node(side.id()) != null ? SchemaDiff.neighbourhood(side.home(), side.id()) : Map.of();
    }
}
