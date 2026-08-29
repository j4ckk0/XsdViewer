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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.workspace.Workspace;

/**
 * {@code GET /api/initial}: the file given on the command line, so the page can open it at
 * start-up: {@code {name, path, text}} for a schema, the workspace answer (see
 * {@link WorkspaceResponse}) for a {@code *.xsdviewer.json} workspace file.
 */
final class InitialFileHandler implements HttpHandler {

    private final ServedSchemaFiles files;
    private final Path initialFile;

    InitialFileHandler(ServedSchemaFiles files, Path initialFile) {
        this.files = files;
        this.initialFile = initialFile;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (initialFile == null) {
            HttpResponses.error(ex, HttpStatus.NOT_FOUND, Messages.get(MessageKey.NO_INITIAL_FILE));
            return;
        }
        if (initialFile.getFileName().toString().endsWith(Workspace.FILE_SUFFIX)) {
            String text = Files.readString(initialFile, StandardCharsets.UTF_8);
            if (Workspace.looksLikeWorkspace(text)) {
                HttpResponses.json(ex, HttpStatus.OK, WorkspaceResponse.json(Workspace.fromJson(text, initialFile), initialFile, files));
                return;
            }
        }
        files.send(ex, initialFile);
    }
}
