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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/**
 * The schema files this server has read from disk and handed to the page. Their directories are
 * the only places {@code /api/open} and {@code /api/locate} look into: the page cannot make the
 * server read arbitrary paths.
 */
final class ServedSchemaFiles {

    private final Set<Path> files = ConcurrentHashMap.newKeySet();

    void remember(Path file) {
        files.add(file.toAbsolutePath().normalize());
    }

    boolean contains(Path file) {
        return files.contains(file.toAbsolutePath().normalize());
    }

    /** The directories of the served files, without duplicates, in no particular order. */
    List<Path> directories() {
        List<Path> dirs = new ArrayList<>();
        for (Path f : files) {
            Path dir = f.getParent();
            if (dir != null && !dirs.contains(dir)) dirs.add(dir);
        }
        return dirs;
    }

    /** Answers {@code {name, path, text}} for a schema file and remembers it. */
    void send(HttpExchange ex, Path file) throws IOException {
        JsonWriter w = new JsonWriter(4096);
        writeFile(w, file);
        HttpResponses.json(ex, HttpStatus.OK, w.toString());
    }

    /** Writes {@code {name, path, text}} of a schema file into {@code w} and remembers the file. */
    void writeFile(JsonWriter w, Path file) throws IOException {
        Path abs = file.toAbsolutePath().normalize();
        String text = Files.readString(abs, StandardCharsets.UTF_8);
        remember(abs);
        w.beginObject()
                .property(JsonKey.NAME, abs.getFileName().toString())
                .property(JsonKey.PATH, abs.toString())
                .property(JsonKey.TEXT, text)
                .endObject();
    }
}
