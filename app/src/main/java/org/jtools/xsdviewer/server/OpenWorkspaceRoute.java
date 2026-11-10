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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jtools.framework.desktop.FileDialogs;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.workspace.Workspace;

import org.jtools.framework.server.Exchange;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Json;

/** {@code POST /api/workspace/open}: the native "open" dialog for a workspace file, answered with its schemas. 409 without a display. */
final class OpenWorkspaceRoute implements WebServer.Route {

    private final ServedSchemaFiles files;

    OpenWorkspaceRoute(ServedSchemaFiles files) {
        this.files = files;
    }

    /** What the workspace dialogs let pick: {@code *.xsdviewer.json}. */
    static FileDialogs.Filter workspaceFilter() {
        return new FileDialogs.Filter(Messages.get(MessageKey.DIALOG_FILTER_WORKSPACES), List.of("*" + Workspace.FILE_SUFFIX));
    }

    @Override
    public Object answer(Exchange ex) throws IOException {
        if (!FileDialogs.available()) throw new WebServer.Refusal(HttpStatus.CONFLICT, Messages.get(MessageKey.NO_DISPLAY));
        List<Path> chosen = FileDialogs.chooseFilesToOpen(Messages.get(MessageKey.DIALOG_OPEN_WORKSPACE), false, workspaceFilter());
        if (chosen.isEmpty()) return Json.raw(JsonWriter.object(JsonKey.CANCELLED, true));
        Path file = chosen.get(0);
        try {
            Workspace ws = Workspace.fromJson(Files.readString(file, StandardCharsets.UTF_8), file);
            return Json.raw(WorkspaceResponse.json(ws, file, files));
        } catch (IllegalArgumentException e) {
            throw new WebServer.Refusal(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
