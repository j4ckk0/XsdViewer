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
import org.jtools.xsdviewer.compare.WorkspacePairing;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * {@code POST /api/compare/workspaces}, body {@code {"left": [{"name", "text"}...], "right": [...],
 * "businessOnly": bool}}: the files of two workspaces paired by name, each pair with its status
 * ({@link WorkspacePairing}). Answers {@code {"pairs": [{"name", "status"}...]}}, the names sorted.
 */
final class CompareWorkspacesHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Map<String, Object> request = JsonRequest.of(ex);
        if (request == null) return;
        JsonWriter w = new JsonWriter();
        CompareJsonWriter.pairs(w, WorkspacePairing.of(RequestBody.byName(request.get(JsonKey.LEFT)), RequestBody.byName(request.get(JsonKey.RIGHT)),
                RequestBody.flag(request, JsonKey.BUSINESS_ONLY)));
        HttpResponses.json(ex, HttpStatus.OK, w.toString());
    }
}
