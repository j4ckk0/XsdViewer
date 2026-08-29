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
