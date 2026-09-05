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

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.model.Box;
import org.jtools.xsdviewer.model.BoxJsonWriter;
import org.jtools.xsdviewer.model.ContentTree;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.jtools.xsdviewer.server.RequestBody.Side;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * {@code POST /api/model}, body {@code {"files": [{"name", "text"}...], "home": n, "id": "...",
 * "expanded": [paths], "openAll": bool}}: the content model tree of the declaration {@code id} of the
 * file at {@code home}, its boxes opened as {@code expanded} says (or all of them), the named types it
 * uses read from the other files. Answers the tree as JSON, or {@code 400} when the home file is not a
 * schema or declares no such id.
 */
final class ModelHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Map<String, Object> request = JsonRequest.of(ex);
        if (request == null) return;
        Side side = RequestBody.side(request);
        Node root = side.home() != null ? side.home().node(side.id()) : null;
        if (root == null) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.DECLARATION_EXPECTED, side.id()));
            return;
        }
        Box tree = ContentTree.build(root, side.home(), side.library(), RequestBody.expanded(request), RequestBody.flag(request, JsonKey.OPEN_ALL));
        JsonWriter w = new JsonWriter();
        BoxJsonWriter.write(w, tree);
        HttpResponses.json(ex, HttpStatus.OK, w.toString());
    }
}
