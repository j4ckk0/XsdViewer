package org.jtools.xsdviewer.workspace;

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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;

/**
 * A workspace as saved in {@code <name>.xsdviewer.json}: its schema files and which one is shown,
 * with paths relative to that file when they share its root (a folder and its workspace move together).
 *
 * @param files  absolute paths, in tab order
 * @param active index in {@code files} of the file shown
 */
public record Workspace(List<Path> files, int active) {

    public static final String FILE_SUFFIX = ".xsdviewer.json";
    public static final String DEFAULT_FILE_NAME = "workspace" + FILE_SUFFIX;
    /** Version of the format, value of the {@code xsdviewer} marker property. */
    public static final int FORMAT_VERSION = 1;
    private static final String PORTABLE_SEPARATOR = "/";

    /** The JSON of this workspace, to be written at {@code file}. */
    public String toJson(Path file) {
        Path dir = file.toAbsolutePath().normalize().getParent();
        JsonWriter w = new JsonWriter().beginObject().property(JsonKey.WORKSPACE_MARKER, FORMAT_VERSION);
        w.name(JsonKey.FILES).beginArray();
        for (Path f : files) w.value(portable(dir, f));
        w.endArray();
        return w.property(JsonKey.ACTIVE, active).endObject().toString();
    }

    /** {@code f} relative to {@code dir} when possible, with '/' separators. */
    private static String portable(Path dir, Path f) {
        Path abs = f.toAbsolutePath().normalize();
        Path shown = abs;
        if (dir != null && dir.getRoot() != null && dir.getRoot().equals(abs.getRoot())) shown = dir.relativize(abs);
        return shown.toString().replace(abs.getFileSystem().getSeparator(), PORTABLE_SEPARATOR);
    }

    /** True when {@code text} is a JSON object carrying the {@code xsdviewer} marker. */
    public static boolean looksLikeWorkspace(String text) {
        try {
            Map<String, Object> o = JsonReader.asObject(JsonReader.parse(text));
            return o != null && o.containsKey(JsonKey.WORKSPACE_MARKER);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Reads a workspace; relative paths are resolved against the directory of {@code file}.
     *
     * @throws IllegalArgumentException when the text is not a workspace
     */
    public static Workspace fromJson(String text, Path file) {
        Map<String, Object> o = JsonReader.asObject(JsonReader.parse(text));
        List<Object> list = o == null ? null : JsonReader.asArray(o.get(JsonKey.FILES));
        if (o == null || !o.containsKey(JsonKey.WORKSPACE_MARKER) || list == null) {
            throw new IllegalArgumentException(Messages.get(MessageKey.NOT_A_WORKSPACE, file));
        }
        Path dir = file.toAbsolutePath().normalize().getParent();
        List<Path> files = new ArrayList<>();
        for (Object item : list) {
            String s = JsonReader.asString(item);
            if (s == null || s.isEmpty()) continue;
            Path p = Path.of(s);
            files.add((dir == null || p.isAbsolute() ? p : dir.resolve(p)).toAbsolutePath().normalize());
        }
        int active = JsonReader.asInt(o.get(JsonKey.ACTIVE), 0);
        return new Workspace(files, active >= 0 && active < files.size() ? active : 0);
    }
}
