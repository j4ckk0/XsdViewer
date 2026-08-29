package org.jtools.xsdviewer.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;

/**
 * {@code GET /api/open?base=<path>&location=<schemaLocation>}: the schema at {@code location},
 * so the page can follow a link into an imported / included file. The location is tried
 * relative to the directory of {@code base} (when it is a file this server already served),
 * then to the directories of all the files it served, then to the working directory: a file
 * opened from the browser has no known path, but its imports usually sit next to something
 * the server knows. With {@code strict=true} only the directory of {@code base} is tried (the
 * location must be relative to the referencing file). Remote locations (http://...) are refused:
 * the tool never goes on the network.
 */
final class OpenSchemaLocationHandler implements HttpHandler {

    private static final String REMOTE_LOCATION_MARK = "://";

    private final ServedSchemaFiles files;

    OpenSchemaLocationHandler(ServedSchemaFiles files) {
        this.files = files;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        QueryString q = QueryString.of(ex);
        String base = q.get(ApiPath.PARAM_BASE), location = q.get(ApiPath.PARAM_LOCATION);
        if (location.isEmpty()) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.LOCATION_EXPECTED));
            return;
        }
        if (location.contains(REMOTE_LOCATION_MARK)) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.REMOTE_LOCATION_NOT_SUPPORTED, location));
            return;
        }
        String rel = location.replace('\\', '/');
        boolean strict = ApiPath.TRUE.equals(q.get(ApiPath.PARAM_STRICT));
        for (Path dir : searchDirectories(base, strict)) {
            Path target = dir.resolve(rel).normalize();
            if (Files.isRegularFile(target)) {
                files.send(ex, target);
                return;
            }
        }
        HttpResponses.error(ex, HttpStatus.NOT_FOUND, Messages.get(MessageKey.FILE_NOT_FOUND, location));
    }

    private List<Path> searchDirectories(String base, boolean strict) {
        List<Path> dirs = new ArrayList<>();
        if (!base.isEmpty()) {
            Path basePath = Path.of(base).toAbsolutePath().normalize();
            if (files.contains(basePath) && basePath.getParent() != null) dirs.add(basePath.getParent());
        }
        if (strict) return dirs;
        dirs.addAll(files.directories());
        dirs.add(Path.of("").toAbsolutePath());
        return dirs;
    }
}
