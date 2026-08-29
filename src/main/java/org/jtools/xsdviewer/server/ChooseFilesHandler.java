package org.jtools.xsdviewer.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/**
 * {@code POST /api/choose}: shows the native "open files" dialog and answers
 * {@code {"files": [{name, path, text}...]}} (empty when cancelled), so that files opened by the
 * user come with their location. 409 when the server has no display.
 */
final class ChooseFilesHandler implements HttpHandler {

    private static final String SCHEMA_EXTENSION = ".xsd";
    private static final String XML_EXTENSION = ".xml";

    private final ServedSchemaFiles files;

    ChooseFilesHandler(ServedSchemaFiles files) {
        this.files = files;
    }

    static boolean isSchemaName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(SCHEMA_EXTENSION) || n.endsWith(XML_EXTENSION);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        if (!FileDialogs.available()) {
            HttpResponses.error(ex, HttpStatus.CONFLICT, Messages.get(MessageKey.NO_DISPLAY));
            return;
        }
        List<Path> chosen = FileDialogs.chooseFilesToOpen(Messages.get(MessageKey.DIALOG_OPEN_SCHEMA), true, ChooseFilesHandler::isSchemaName);
        JsonWriter w = new JsonWriter(4096).beginObject().name(JsonKey.FILES).beginArray();
        for (Path p : chosen) files.writeFile(w, p);
        HttpResponses.json(ex, HttpStatus.OK, w.endArray().endObject().toString());
    }
}
