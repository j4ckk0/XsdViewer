package org.jtools.xsdviewer.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;

/**
 * Serves the page (HTML, scripts, styles, translations) from the {@code web/} directory of the
 * classpath. Paths are restricted to plain names and sub-directories ({@code /js/app.js}); anything
 * else, including {@code ..}, is a 404. Answers are marked {@code no-cache} so that a rebuilt jar
 * is picked up by a simple reload.
 */
final class StaticResourceHandler implements HttpHandler {

    private static final String RESOURCE_ROOT = "/web";
    private static final String INDEX_PAGE = "/index.html";
    private static final String PARENT_DIRECTORY = "..";
    private static final Pattern ALLOWED_PATH = Pattern.compile("(/[A-Za-z0-9._-]+)+");

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals(ApiPath.ROOT)) path = INDEX_PAGE;
        if (path.contains(PARENT_DIRECTORY) || !ALLOWED_PATH.matcher(path).matches()) {
            notFound(ex);
            return;
        }
        try (InputStream in = StaticResourceHandler.class.getResourceAsStream(RESOURCE_ROOT + path)) {
            if (in == null) {
                notFound(ex);
                return;
            }
            HttpResponses.bytes(ex, HttpStatus.OK, ContentType.forFile(path), in.readAllBytes(), true);
        }
    }

    private static void notFound(HttpExchange ex) throws IOException {
        HttpResponses.text(ex, HttpStatus.NOT_FOUND, Messages.get(MessageKey.NOT_FOUND));
    }
}
