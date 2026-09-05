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

import java.util.ArrayDeque;
import java.util.Deque;

/** Minimal streaming JSON writer, enough for the flat model exchanged with the page: no library dependency. */
public final class JsonWriter {

    private final StringBuilder sb;
    /** For each open container, whether a value has already been written in it (a comma is then needed). */
    private final Deque<boolean[]> containers = new ArrayDeque<>();

    public JsonWriter() {
        this(64);
    }

    public JsonWriter(int capacity) {
        sb = new StringBuilder(capacity);
    }

    public JsonWriter beginObject() {
        separate();
        sb.append('{');
        containers.push(new boolean[1]);
        return this;
    }

    public JsonWriter endObject() {
        containers.pop();
        sb.append('}');
        return this;
    }

    public JsonWriter beginArray() {
        separate();
        sb.append('[');
        containers.push(new boolean[1]);
        return this;
    }

    public JsonWriter endArray() {
        containers.pop();
        sb.append(']');
        return this;
    }

    /** The key of the next value of the current object. */
    public JsonWriter name(String key) {
        separate();
        JsonStrings.quote(sb, key);
        sb.append(':');
        containers.peek()[0] = false;   // the value that follows must not be preceded by a comma
        return this;
    }

    public JsonWriter value(String s) {
        separate();
        JsonStrings.quote(sb, s);
        return this;
    }

    /** The JSON {@code null}: a side that has nothing, a diff that could not be computed. */
    public JsonWriter nullValue() {
        separate();
        sb.append("null");
        return this;
    }

    public JsonWriter value(int i) {
        separate();
        sb.append(i);
        return this;
    }

    public JsonWriter value(boolean b) {
        separate();
        sb.append(b);
        return this;
    }

    /** {@code name(key).value(s)} */
    public JsonWriter property(String key, String s) {
        return name(key).value(s);
    }

    public JsonWriter property(String key, int i) {
        return name(key).value(i);
    }

    public JsonWriter property(String key, boolean b) {
        return name(key).value(b);
    }

    /** One-object document {@code {"key": value}}. */
    public static String object(String key, String value) {
        return new JsonWriter().beginObject().property(key, value).endObject().toString();
    }

    public static String object(String key, boolean value) {
        return new JsonWriter().beginObject().property(key, value).endObject().toString();
    }

    private void separate() {
        boolean[] c = containers.peek();
        if (c == null) return;
        if (c[0]) sb.append(',');
        c[0] = true;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
