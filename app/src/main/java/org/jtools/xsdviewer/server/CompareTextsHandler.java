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
import org.jtools.xsdviewer.compare.TextComparison;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * {@code POST /api/compare/texts}, body {@code {"left": text, "right": text, "businessOnly": bool,
 * "ignoreSpacing": bool}}: the two texts compared line by line ({@link TextComparison}). Answers
 * {@code {"la": [{n, text}], "lb": [...], "ops": [op...] | null, "onlyMoves": bool}} — the lines of
 * each side with their number in the original text, the edit script (null when the texts are too
 * different to align), and whether the two differ only by moved blocks.
 */
final class CompareTextsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Map<String, Object> request = JsonRequest.of(ex);
        if (request == null) return;
        TextComparison.Result r = TextComparison.of(RequestBody.text(request, JsonKey.LEFT), RequestBody.text(request, JsonKey.RIGHT),
                RequestBody.flag(request, JsonKey.BUSINESS_ONLY), RequestBody.flag(request, JsonKey.IGNORE_SPACING));
        JsonWriter w = new JsonWriter();
        CompareJsonWriter.texts(w, r);
        HttpResponses.json(ex, HttpStatus.OK, w.toString());
    }
}
