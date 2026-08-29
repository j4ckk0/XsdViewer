package org.jtools.xsdviewer.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.workspace.Workspace;

/**
 * {@code POST /api/workspace/open}: shows the native "open" dialog for a workspace file and
 * answers it with its schemas (see {@link WorkspaceResponse}), {@code {"cancelled": true}} when
 * the user cancels, 400 when the file is not a workspace, 409 without a display.
 */
final class OpenWorkspaceHandler implements HttpHandler {

    private final ServedSchemaFiles files;

    OpenWorkspaceHandler(ServedSchemaFiles files) {
        this.files = files;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        if (!FileDialogs.available()) {
            HttpResponses.error(ex, HttpStatus.CONFLICT, Messages.get(MessageKey.NO_DISPLAY));
            return;
        }
        List<Path> chosen = FileDialogs.chooseFilesToOpen(Messages.get(MessageKey.DIALOG_OPEN_WORKSPACE), false,
                name -> name.endsWith(Workspace.FILE_SUFFIX));
        if (chosen.isEmpty()) {
            HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.CANCELLED, true));
            return;
        }
        Path file = chosen.get(0);
        try {
            Workspace ws = Workspace.fromJson(Files.readString(file, StandardCharsets.UTF_8), file);
            HttpResponses.json(ex, HttpStatus.OK, WorkspaceResponse.json(ws, file, files));
        } catch (IllegalArgumentException e) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
