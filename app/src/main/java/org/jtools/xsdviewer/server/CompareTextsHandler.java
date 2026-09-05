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
import java.util.regex.Pattern;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.compare.BusinessLines;
import org.jtools.xsdviewer.compare.BusinessLines.Line;
import org.jtools.xsdviewer.compare.LineDiff;
import org.jtools.xsdviewer.compare.LineDiff.Op;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * {@code POST /api/compare/texts}, body {@code {"left": text, "right": text, "businessOnly": bool,
 * "ignoreSpacing": bool}}: the two texts compared line by line. {@code businessOnly} compares the
 * business lines only ({@link BusinessLines}); {@code ignoreSpacing} matches lines on their shape,
 * indentation and runs of blanks ignored, while the lines answered stay as written — two declarations
 * at different depths then match line for line. Answers {@code {"la": [{n, text}], "lb": [...], "ops":
 * [op...] | null, "onlyMoves": bool}}: the lines of each side with their number in the original text,
 * the edit script ({@link LineDiff}; null when the texts are too different to align), and whether the
 * two differ only by moved blocks.
 */
final class CompareTextsHandler implements HttpHandler {

    private static final Pattern SPACING = Pattern.compile("\\s+");
    private static final String CANONICAL_LINE_BREAK = "\n";

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
        boolean businessOnly = RequestFiles.flag(request, JsonKey.BUSINESS_ONLY), ignoreSpacing = RequestFiles.flag(request, JsonKey.IGNORE_SPACING);
        List<Line> la = lines(JsonReader.asString(request.get(JsonKey.LEFT)), businessOnly);
        List<Line> lb = lines(JsonReader.asString(request.get(JsonKey.RIGHT)), businessOnly);
        List<Op> ops = LineDiff.diff(compared(la, ignoreSpacing), compared(lb, ignoreSpacing));
        JsonWriter w = new JsonWriter().beginObject();
        w.name(JsonKey.LA).beginArray();
        for (Line l : la) l.write(w);
        w.endArray().name(JsonKey.LB).beginArray();
        for (Line l : lb) l.write(w);
        w.endArray().name(JsonKey.OPS);
        if (ops == null) {
            w.nullValue();
        } else {
            w.beginArray();
            for (Op o : ops) o.write(w);
            w.endArray();
        }
        w.property(JsonKey.ONLY_MOVES, ops != null && LineDiff.onlyMoves(ops)).endObject();
        HttpResponses.json(ex, HttpStatus.OK, w.toString());
    }

    /** The lines compared, with their original numbers: every line, or the business lines only; a Windows line ending is a line ending. */
    static List<Line> lines(String text, boolean businessOnly) {
        String canonical = text.replace("\r\n", CANONICAL_LINE_BREAK);
        return businessOnly ? BusinessLines.of(canonical) : BusinessLines.all(canonical);
    }

    /** What the diff matches: the lines themselves, or their shape. */
    private static List<String> compared(List<Line> lines, boolean ignoreSpacing) {
        return lines.stream().map(l -> ignoreSpacing ? SPACING.matcher(l.text()).replaceAll(" ").trim() : l.text()).toList();
    }
}
