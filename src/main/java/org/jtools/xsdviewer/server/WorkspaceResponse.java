package org.jtools.xsdviewer.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.workspace.Workspace;

/**
 * The answer describing an opened workspace: {@code {"workspace": path, "active": n,
 * "files": [{name, path, text}...], "missing": [paths...]}}. Files that no longer exist are
 * listed under {@code missing} instead of failing the whole workspace.
 */
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
