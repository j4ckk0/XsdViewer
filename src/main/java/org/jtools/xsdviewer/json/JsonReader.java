package org.jtools.xsdviewer.json;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;

/** Minimal JSON parser (objects → {@code Map}, arrays → {@code List}, numbers → {@code Long} / {@code Double}), enough for the workspace files and the page's requests: no library dependency. */
public final class JsonReader {

    private final String text;
    private int pos;

    private JsonReader(String text) {
        this.text = text;
    }

    /** @throws IllegalArgumentException when the text is not valid JSON */
    public static Object parse(String text) {
        JsonReader r = new JsonReader(text);
        r.skipWhitespace();
        Object value = r.readValue();
        r.skipWhitespace();
        if (r.pos != text.length()) throw r.error();
        return value;
    }

    /** {@code value} as an object, or null when it is something else. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    /** {@code value} as an array, or null when it is something else. */
    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    public static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    /** {@code value} as an int, or {@code fallback} when it is not a number. */
    public static int asInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private Object readValue() {
        if (pos >= text.length()) throw error();
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) yield readNumber();
                throw error();
            }
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        for (;;) {
            skipWhitespace();
            if (peek() != '"') throw error();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') return map;
            if (c != ',') throw error();
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        for (;;) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') return list;
            if (c != ',') throw error();
        }
    }

    private String readString() {
        pos++;   // opening quote
        StringBuilder sb = new StringBuilder();
        for (;;) {
            char c = next();
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char e = next();
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (pos + 4 > text.length()) throw error();
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error();
                    }
                    pos += 4;
                }
                default -> throw error();
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
        boolean decimal = false;
        if (pos < text.length() && text.charAt(pos) == '.') {
            decimal = true;
            pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            decimal = true;
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
        }
        String s = text.substring(start, pos);
        try {
            return decimal ? (Object) Double.parseDouble(s) : (Object) Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw error();
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, pos)) throw error();
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= text.length()) throw error();
        return text.charAt(pos);
    }

    private char next() {
        if (pos >= text.length()) throw error();
        return text.charAt(pos++);
    }

    private void expect(char c) {
        if (next() != c) throw error();
    }

    private IllegalArgumentException error() {
        return new IllegalArgumentException(Messages.get(MessageKey.INVALID_JSON, pos));
    }
}
