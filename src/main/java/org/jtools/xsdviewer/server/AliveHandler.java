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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;

/**
 * {@code GET /api/alive?id=}: a page's presence, as an event stream the page keeps open for
 * its whole life. The handler writes a comment every {@link #PING_MILLIS} and returns when
 * the write fails, i.e. when the browser has closed the page (or crashed, or the machine
 * went away): no timer on the page is involved, so a background tab is never mistaken for a
 * closed one. The page is counted in {@link PageWatch} between the two.
 */
final class AliveHandler implements HttpHandler {

    static final long PING_MILLIS = 5_000;
    static final String CONTENT_TYPE = "text/event-stream";
    /** An SSE comment line: ignored by the page, enough to notice a closed socket. */
    private static final byte[] PING = ": ping\n\n".getBytes(StandardCharsets.UTF_8);
    private static final long CHUNKED = 0;

    private final PageWatch pages;

    AliveHandler(PageWatch pages) {
        this.pages = pages;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String id = QueryString.of(ex).get(ApiPath.PARAM_ID);
        if (id.isEmpty()) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.PAGE_ID_EXPECTED));
            return;
        }
        ex.getResponseHeaders().set(ContentType.HEADER, CONTENT_TYPE);
        ex.getResponseHeaders().set(HttpResponses.CACHE_CONTROL_HEADER, HttpResponses.NO_CACHE);
        ex.sendResponseHeaders(HttpStatus.OK, CHUNKED);
        pages.opened(id);
        try (OutputStream out = ex.getResponseBody()) {
            while (true) {
                out.write(PING);
                out.flush();
                Thread.sleep(PING_MILLIS);
            }
        } catch (IOException | InterruptedException gone) {
            // the page is gone (or the server is stopping): nothing to answer any more
        } finally {
            pages.closed(id);
        }
    }
}
