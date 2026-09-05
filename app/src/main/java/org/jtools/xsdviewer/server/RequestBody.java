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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.model.Library;
import org.jtools.xsdviewer.model.Library.File;
import org.jtools.xsdviewer.schema.SchemaException;

/**
 * Reading what a model or comparison request carries. The requests are stateless: a body holds the
 * files themselves — {@code "files": [{"name", "text"}...]} — and names one of them by its index,
 * never a file of the server's own.
 */
final class RequestBody {

    /** The files of one side, the one its declaration is read from, and which declaration; {@code home} is null when that file is not a schema. */
    record Side(Library library, File home, String id) {}

    private RequestBody() {}

    /**
     * One side of a request: its files parsed into a library, the file at {@code home}, the
     * declaration {@code id}. A text that is not a schema is left out of the library — the page only
     * lists parsed files, but a request is not trusted to — while keeping the others' indexes.
     */
    static Side side(Map<String, Object> request) {
        List<File> slots = new ArrayList<>();
        for (Object o : JsonReader.asArray(request.get(JsonKey.FILES))) {
            Map<String, Object> f = JsonReader.asObject(o);
            File file;
            try {
                file = new File(JsonReader.asString(f.get(JsonKey.NAME)), ParsedSchemas.of(JsonReader.asString(f.get(JsonKey.TEXT))));
            } catch (SchemaException e) {
                file = null;
            }
            slots.add(file);
        }
        List<File> parsed = new ArrayList<>();
        for (File f : slots) if (f != null) parsed.add(f);
        int home = JsonReader.asInt(request.get(JsonKey.HOME), -1);
        return new Side(new Library(parsed), home >= 0 && home < slots.size() ? slots.get(home) : null, JsonReader.asString(request.get(JsonKey.ID)));
    }

    /** The texts of a list of files by name, the first of a name kept: what a workspace holds, for the pairing. */
    static Map<String, String> byName(Object files) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Object o : JsonReader.asArray(files)) {
            Map<String, Object> f = JsonReader.asObject(o);
            out.putIfAbsent(JsonReader.asString(f.get(JsonKey.NAME)), JsonReader.asString(f.get(JsonKey.TEXT)));
        }
        return out;
    }

    /** The paths of the boxes a request says are open. */
    static Set<String> expanded(Map<String, Object> request) {
        Set<String> paths = new HashSet<>();
        Object list = request.get(JsonKey.EXPANDED);
        if (list != null) for (Object o : JsonReader.asArray(list)) paths.add(JsonReader.asString(o));
        return paths;
    }

    static boolean flag(Map<String, Object> request, String key) {
        return Boolean.TRUE.equals(request.get(key));
    }

    static String text(Map<String, Object> request, String key) {
        return JsonReader.asString(request.get(key));
    }
}
