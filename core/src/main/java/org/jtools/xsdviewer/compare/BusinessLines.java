package org.jtools.xsdviewer.compare;

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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/**
 * The "business" lines of a schema text: what remains once XML comments and xs:annotation blocks
 * (documentation, appinfo) are removed, the wiring tags dropped (the XML declaration, the xs:schema
 * root tags, xs:import and xs:include), blank lines dropped and indentation ignored — the lines
 * that define the schema, for comparing two versions without the noise.
 */
public final class BusinessLines {

    /** A line as compared: its 1-based number in the original text, and its text. */
    public record Line(int n, String text) {
        public void write(JsonWriter w) {
            w.beginObject().property(JsonKey.N, n).property(JsonKey.TEXT, text).endObject();
        }
    }

    private static final String COMMENT_START = "<!--", COMMENT_END = "-->";
    private static final Pattern ANNOTATION_START = Pattern.compile("<(?:[\\w.-]+:)?annotation(?=[\\s>/])");
    private static final Pattern ANNOTATION_END = Pattern.compile("</(?:[\\w.-]+:)?annotation\\s*>");
    /** A tag dropped up to its ">" (its content, for xs:schema, stays): "<?xml", "<xs:schema", "</xs:schema>", "<xs:import", "<xs:include". */
    private static final Pattern DROPPED_TAG_START = Pattern.compile("<(?:\\?xml|/?(?:[\\w.-]+:)?(?:schema|import|include))(?=[\\s>/?])");
    private static final String SELF_CLOSING_END = "/>", TAG_END = ">";
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private BusinessLines() {}

    /** Every line of the text, numbered: what is compared when nothing is filtered. */
    public static List<Line> all(String text) {
        List<Line> out = new ArrayList<>();
        int n = 1;
        for (String line : LineDiff.splitLines(text)) out.add(new Line(n++, line));
        return out;
    }

    /** The business lines with their number in the original text, whitespace runs collapsed. */
    public static List<Line> of(String text) {
        List<Line> out = new ArrayList<>();
        boolean inComment = false, inAnnotation = false, inDroppedTag = false;
        int number = 0;
        for (String line : LineDiff.splitLines(text)) {
            number++;
            StringBuilder kept = new StringBuilder();
            String rest = line;
            while (!rest.isEmpty()) {
                if (inComment) {
                    int end = rest.indexOf(COMMENT_END);
                    if (end < 0) {
                        rest = "";
                        break;
                    }
                    rest = rest.substring(end + COMMENT_END.length());
                    inComment = false;
                } else if (inAnnotation) {
                    Matcher m = ANNOTATION_END.matcher(rest);
                    if (!m.find()) {
                        rest = "";
                        break;
                    }
                    rest = rest.substring(m.end());
                    inAnnotation = false;
                } else if (inDroppedTag) {
                    int end = rest.indexOf(TAG_END);
                    if (end < 0) {
                        rest = "";
                        break;
                    }
                    rest = rest.substring(end + TAG_END.length());
                    inDroppedTag = false;
                } else {
                    int comment = rest.indexOf(COMMENT_START);
                    Matcher annotation = ANNOTATION_START.matcher(rest);
                    int annotationAt = annotation.find() ? annotation.start() : -1;
                    Matcher dropped = DROPPED_TAG_START.matcher(rest);
                    int droppedAt = dropped.find() ? dropped.start() : -1;
                    int next = first(comment, annotationAt, droppedAt);
                    if (next < 0) {
                        kept.append(rest);
                        rest = "";
                        break;
                    }
                    kept.append(rest, 0, next);
                    rest = rest.substring(next);
                    if (next == comment) {
                        inComment = true;
                        rest = rest.substring(COMMENT_START.length());
                    } else if (next == droppedAt) {
                        inDroppedTag = true;   // dropped up to its ">" above, on this line or a later one
                    } else {
                        int close = rest.indexOf(TAG_END);
                        if (close >= 0 && rest.substring(0, close + 1).endsWith(SELF_CLOSING_END)) {
                            rest = rest.substring(close + 1);   // <xs:annotation/>
                        } else {
                            inAnnotation = true;
                            rest = close >= 0 ? rest.substring(close + 1) : "";
                        }
                    }
                }
            }
            String business = WHITESPACE_RUN.matcher(kept).replaceAll(" ").trim();
            if (!business.isEmpty()) out.add(new Line(number, business));
        }
        return out;
    }

    /** The first of the positions that are not -1, or -1. */
    private static int first(int... positions) {
        int best = -1;
        for (int p : positions) if (p >= 0 && (best < 0 || p < best)) best = p;
        return best;
    }
}
