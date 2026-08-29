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
import java.nio.file.Path;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/**
 * {@code POST /api/locate?name=<file name>}, body = the file's text: finds where a file the
 * user opened in the browser (which hides its folder) is on disk, so that its imports can be
 * followed. Looks for a file with that name and the same content under the directories of the
 * files already served and the working directory, answering {@code {"path"}} or 404.
 */
final class LocateSchemaFileHandler implements HttpHandler {

    private final ServedSchemaFiles files;
    private final SchemaFileFinder finder;

    LocateSchemaFileHandler(ServedSchemaFiles files, SchemaFileFinder finder) {
        this.files = files;
        this.finder = finder;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        String name = QueryString.of(ex).get(ApiPath.PARAM_NAME);
        String text = HttpResponses.readBody(ex);
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.FILE_NAME_EXPECTED));
            return;
        }
        List<Path> roots = files.directories();
        roots.add(Path.of("").toAbsolutePath());
        String wanted = SchemaFileFinder.canonical(text);
        for (Path root : roots) {
            Path found = finder.find(root, name, wanted);
            if (found != null) {
                files.remember(found);
                HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.PATH, found.toString()));
                return;
            }
        }
        HttpResponses.error(ex, HttpStatus.NOT_FOUND, Messages.get(MessageKey.NO_FILE_WITH_CONTENT, name, roots));
    }
}
