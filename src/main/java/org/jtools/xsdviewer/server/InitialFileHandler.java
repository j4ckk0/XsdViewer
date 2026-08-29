package org.jtools.xsdviewer.server;

import java.io.IOException;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;

/** {@code GET /api/initial}: the file given on the command line, so the page can open it at start-up. */
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
        files.send(ex, initialFile);
    }
}
