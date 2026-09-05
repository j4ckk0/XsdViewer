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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.workspace.Workspace;

/** {@code POST /api/workspace/save}: writes the workspace the page describes where the native "save as" dialog says (its own file proposed). 409 without a display. */
final class SaveWorkspaceHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        if (!FileDialogs.available()) {
            HttpResponses.error(ex, HttpStatus.CONFLICT, Messages.get(MessageKey.NO_DISPLAY));
            return;
        }
        Workspace ws;
        Path suggested;
        try {
            Map<String, Object> body = JsonReader.asObject(JsonReader.parse(HttpResponses.readBody(ex)));
            ws = fromRequest(body);
            String s = body == null ? null : JsonReader.asString(body.get(JsonKey.PATH));
            suggested = s == null || s.isEmpty() ? null : Path.of(s).toAbsolutePath().normalize();
        } catch (IllegalArgumentException e) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, e.getMessage());
            return;
        }
        Path dir = suggested != null ? suggested.getParent() : ws.files().isEmpty() ? null : ws.files().get(0).getParent();
        String name = suggested != null ? suggested.getFileName().toString() : Workspace.DEFAULT_FILE_NAME;
        Path target = FileDialogs.chooseFileToSave(Messages.get(MessageKey.DIALOG_SAVE_WORKSPACE), dir, name, OpenWorkspaceHandler.workspaceFilter());
        if (target == null) {
            HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.CANCELLED, true));
            return;
        }
        if (!target.getFileName().toString().endsWith(Workspace.FILE_SUFFIX)) {
            target = target.resolveSibling(target.getFileName() + Workspace.FILE_SUFFIX);
        }
        Files.writeString(target, ws.toJson(target), StandardCharsets.UTF_8);
        HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.PATH, target.toString()));
    }

    /** The workspace described by the request body: absolute paths, as the page got them from the server. */
    private static Workspace fromRequest(Map<String, Object> o) {
        List<Object> list = o == null ? null : JsonReader.asArray(o.get(JsonKey.FILES));
        if (list == null) throw new IllegalArgumentException(Messages.get(MessageKey.WORKSPACE_EXPECTED));
        List<Path> files = new ArrayList<>();
        for (Object item : list) {
            String s = JsonReader.asString(item);
            if (s != null && !s.isEmpty()) files.add(Path.of(s).toAbsolutePath().normalize());
        }
        int active = JsonReader.asInt(o.get(JsonKey.ACTIVE), 0);
        return new Workspace(files, active >= 0 && active < files.size() ? active : 0);
    }
}
