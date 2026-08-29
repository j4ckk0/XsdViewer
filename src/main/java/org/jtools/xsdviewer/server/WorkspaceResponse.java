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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.workspace.Workspace;

/** The answer describing an opened workspace: its schemas, the files that no longer exist listed apart rather than failing the whole workspace. */
final class WorkspaceResponse {

    private WorkspaceResponse() {}

    static String json(Workspace ws, Path workspaceFile, ServedSchemaFiles files) throws IOException {
        List<Path> present = new ArrayList<>(), missing = new ArrayList<>();
        for (Path p : ws.files()) (Files.isRegularFile(p) ? present : missing).add(p);
        Path activeFile = ws.files().isEmpty() ? null : ws.files().get(ws.active());
        int active = Math.max(0, present.indexOf(activeFile));

        JsonWriter w = new JsonWriter(4096).beginObject()
                .property(JsonKey.WORKSPACE, workspaceFile.toAbsolutePath().normalize().toString())
                .property(JsonKey.ACTIVE, active);
        w.name(JsonKey.FILES).beginArray();
        for (Path p : present) files.writeFile(w, p);
        w.endArray().name(JsonKey.MISSING).beginArray();
        for (Path p : missing) w.value(p.toString());
        return w.endArray().endObject().toString();
    }
}
