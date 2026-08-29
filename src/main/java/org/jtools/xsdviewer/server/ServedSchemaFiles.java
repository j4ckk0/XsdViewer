package org.jtools.xsdviewer.server;

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
        Path abs = file.toAbsolutePath().normalize();
        String text = Files.readString(abs, StandardCharsets.UTF_8);
        remember(abs);
        HttpResponses.json(ex, HttpStatus.OK, new JsonWriter(text.length() + 256).beginObject()
                .property(JsonKey.NAME, abs.getFileName().toString())
                .property(JsonKey.PATH, abs.toString())
                .property(JsonKey.TEXT, text)
                .endObject().toString());
    }
}
