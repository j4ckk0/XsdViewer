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

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

import org.jtools.framework.server.Exchange;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Json;

/**
 * {@code POST /api/locate?name=}: finds on disk a file the user opened in the browser (which hides
 * its folder), by name and content under the served directories, so that its imports can be followed.
 */
final class LocateSchemaFileRoute implements WebServer.Route {

    private final ServedSchemaFiles files;
    private final SchemaFileFinder finder;

    LocateSchemaFileRoute(ServedSchemaFiles files, SchemaFileFinder finder) {
        this.files = files;
        this.finder = finder;
    }

    @Override
    public Object answer(Exchange ex) throws IOException {
        String name = ex.query(ApiPath.PARAM_NAME, "");
        String text = ex.text();
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new WebServer.Refusal(HttpStatus.BAD_REQUEST, Messages.get(MessageKey.FILE_NAME_EXPECTED));
        }
        List<Path> roots = files.directories();
        roots.add(Path.of("").toAbsolutePath());
        String wanted = SchemaFileFinder.canonical(text);
        for (Path root : roots) {
            Path found = finder.find(root, name, wanted);
            if (found != null) {
                files.remember(found);
                return Json.raw(JsonWriter.object(JsonKey.PATH, found.toString()));
            }
        }
        throw new WebServer.Refusal(HttpStatus.NOT_FOUND, Messages.get(MessageKey.NO_FILE_WITH_CONTENT, name, roots));
    }
}
