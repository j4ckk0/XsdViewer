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
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;

/** Serves the page from the classpath's {@code web/}: plain names and sub-directories only ({@code ..} is a 404), no-cache so that a rebuilt jar shows on reload. */
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
