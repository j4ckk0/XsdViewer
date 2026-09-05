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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.model.Library;
import org.jtools.xsdviewer.model.Library.File;
import org.jtools.xsdviewer.schema.SchemaException;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaParser;

/**
 * The files a model or comparison request carries, {@code "files": [{"name", "text"}...]}, parsed
 * into a {@link Library}; {@code "home"} is the index, in that list, of the file the declaration is
 * read from. The requests are stateless, so the same texts come back with every click on a box: a
 * graph is kept for a while by the hash of its text, and parsed once.
 */
final class RequestFiles {

    /** How many parsed texts are kept: a workspace's files several times over. */
    private static final int CACHE_SIZE = 500;
    private static final String HASH = "SHA-256";

    private static final Map<String, SchemaGraph> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SchemaGraph> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /** The library of a request and the file it points at; {@code home} is null when that file is not a schema. */
    record Side(Library library, File home, String id) {}

    private RequestFiles() {}

    /** The graph of a text, from the cache or parsed now. */
    static SchemaGraph parse(String text) throws SchemaException {
        String key = hash(text);
        synchronized (CACHE) {
            SchemaGraph cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        SchemaGraph graph = SchemaParser.parse(text);
        synchronized (CACHE) {
            CACHE.put(key, graph);
        }
        return graph;
    }

    /**
     * One side of a request: its {@code files} parsed into a library — a text that is not a schema is
     * left out, the page only listing parsed files but a request not being trusted to —, the file at
     * {@code home} and the declaration {@code id}.
     */
    static Side side(Map<String, Object> request) {
        List<File> slots = new ArrayList<>();
        for (Object o : JsonReader.asArray(request.get(JsonKey.FILES))) {
            Map<String, Object> f = JsonReader.asObject(o);
            File file;
            try {
                file = new File(JsonReader.asString(f.get(JsonKey.NAME)), parse(JsonReader.asString(f.get(JsonKey.TEXT))));
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

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH).digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256 is in every JDK
        }
    }
}
