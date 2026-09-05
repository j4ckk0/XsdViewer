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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.compare.BusinessLines.Line;
import org.jtools.xsdviewer.compare.LineDiff;
import org.jtools.xsdviewer.compare.LineDiff.Op;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * {@code POST /api/compare/workspaces}, body {@code {"left": [{"name", "text"}...], "right": [...],
 * "businessOnly": bool}}: the files of two workspaces paired by name — the first of a name when it
 * appears twice — each pair with its status. Answers {@code {"pairs": [{"name", "status"}...]}}, the
 * names sorted, a status being {@code same} (the compared texts are equal), {@code moved} (they differ
 * by moved blocks only), {@code different}, {@code only-left} or {@code only-right}.
 */
final class CompareWorkspacesHandler implements HttpHandler {

    static final String SAME = "same", DIFFERENT = "different", MOVED = "moved", ONLY_LEFT = "only-left", ONLY_RIGHT = "only-right";

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
        boolean businessOnly = RequestFiles.flag(request, JsonKey.BUSINESS_ONLY);
        Map<String, String> left = byName(request.get(JsonKey.LEFT)), right = byName(request.get(JsonKey.RIGHT));
        TreeSet<String> names = new TreeSet<>(left.keySet());
        names.addAll(right.keySet());
        JsonWriter w = new JsonWriter().beginObject().name(JsonKey.PAIRS).beginArray();
        for (String name : names) {
            w.beginObject().property(JsonKey.NAME, name).property(JsonKey.STATUS, status(left.get(name), right.get(name), businessOnly)).endObject();
        }
        HttpResponses.json(ex, HttpStatus.OK, w.endArray().endObject().toString());
    }

    /** The texts of a side by file name, the first of a name kept. */
    private static Map<String, String> byName(Object files) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Object o : JsonReader.asArray(files)) {
            Map<String, Object> f = JsonReader.asObject(o);
            out.putIfAbsent(JsonReader.asString(f.get(JsonKey.NAME)), JsonReader.asString(f.get(JsonKey.TEXT)));
        }
        return out;
    }

    static String status(String left, String right, boolean businessOnly) {
        if (left == null) return ONLY_RIGHT;
        if (right == null) return ONLY_LEFT;
        List<String> la = CompareTextsHandler.lines(left, businessOnly).stream().map(Line::text).toList();
        List<String> lb = CompareTextsHandler.lines(right, businessOnly).stream().map(Line::text).toList();
        if (la.equals(lb)) return SAME;
        List<Op> ops = LineDiff.diff(la, lb);
        return ops != null && LineDiff.onlyMoves(ops) ? MOVED : DIFFERENT;
    }
}
