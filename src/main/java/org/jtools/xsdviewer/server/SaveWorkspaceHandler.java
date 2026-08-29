package org.jtools.xsdviewer.server;

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

/**
 * {@code POST /api/workspace/save}, body {@code {"files": [paths...], "active": n}}: shows the
 * native "save as" dialog and writes the workspace file there. Answers {@code {"path": ...}},
 * {@code {"cancelled": true}} when the user cancels, 400 for a bad body, 409 without a display.
 */
final class SaveWorkspaceHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        if (!FileDialogs.available()) {
            HttpResponses.error(ex, HttpStatus.CONFLICT, Messages.get(MessageKey.NO_DISPLAY));
            return;
        }
        Workspace ws;
        try {
            ws = fromRequest(HttpResponses.readBody(ex));
        } catch (IllegalArgumentException e) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, e.getMessage());
            return;
        }
        Path dir = ws.files().isEmpty() ? null : ws.files().get(0).getParent();
        Path target = FileDialogs.chooseFileToSave(Messages.get(MessageKey.DIALOG_SAVE_WORKSPACE), dir, Workspace.DEFAULT_FILE_NAME);
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
    private static Workspace fromRequest(String body) {
        Map<String, Object> o = JsonReader.asObject(JsonReader.parse(body));
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
