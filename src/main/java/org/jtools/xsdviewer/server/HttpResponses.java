package org.jtools.xsdviewer.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/** Reading the request body and writing the answers, for all the handlers. */
final class HttpResponses {

    private static final String CACHE_CONTROL_HEADER = "Cache-Control";
    private static final String NO_CACHE = "no-cache";

    private HttpResponses() {}

    static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Answers 405 and returns false unless the request is a POST. */
    static boolean requirePost(HttpExchange ex) throws IOException {
        if (HttpMethod.POST.equals(ex.getRequestMethod())) return true;
        text(ex, HttpStatus.METHOD_NOT_ALLOWED, Messages.get(MessageKey.POST_EXPECTED));
        return false;
    }

    static void text(HttpExchange ex, int status, String body) throws IOException {
        send(ex, status, ContentType.TEXT_PLAIN, body);
    }

    static void json(HttpExchange ex, int status, String json) throws IOException {
        send(ex, status, ContentType.JSON, json);
    }

    /** {@code {"error": message}} */
    static void error(HttpExchange ex, int status, String message) throws IOException {
        json(ex, status, JsonWriter.object(JsonKey.ERROR, message));
    }

    static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        bytes(ex, status, ContentType.withCharset(type), body.getBytes(StandardCharsets.UTF_8), false);
    }

    static void bytes(HttpExchange ex, int status, String contentType, byte[] body, boolean noCache) throws IOException {
        ex.getResponseHeaders().set(ContentType.HEADER, contentType);
        if (noCache) ex.getResponseHeaders().set(CACHE_CONTROL_HEADER, NO_CACHE);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }
}
