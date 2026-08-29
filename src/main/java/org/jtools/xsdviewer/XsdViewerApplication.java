package org.jtools.xsdviewer;

import java.nio.file.Files;

import org.jtools.xsdviewer.server.XsdViewerServer;

/**
 * Entry point: reads the command line, starts the {@link XsdViewerServer} and opens the browser.
 *
 * <pre>
 *   java -jar xsdviewer.jar [--port N] [--host H] [--no-browser] [file.xsd]
 * </pre>
 */
public final class XsdViewerApplication {

    private static final int EXIT_BAD_FILE = 1;
    private static final int EXIT_BAD_USAGE = 2;

    private XsdViewerApplication() {}

    public static void main(String[] args) throws Exception {
        CommandLineOptions options;
        try {
            options = CommandLineOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(Messages.get(MessageKey.USAGE));
            System.exit(EXIT_BAD_USAGE);
            return;
        }
        if (options.help()) {
            System.out.println(Messages.get(MessageKey.USAGE));
            return;
        }
        if (options.initialFile() != null && !Files.isRegularFile(options.initialFile())) {
            System.err.println(Messages.get(MessageKey.NOT_A_FILE, options.initialFile()));
            System.exit(EXIT_BAD_FILE);
            return;
        }

        XsdViewerServer server = XsdViewerServer.start(options.host(), options.port(), options.initialFile());
        System.out.println(Messages.get(MessageKey.SERVER_LISTENING, server.url()));
        if (options.openBrowser()) BrowserLauncher.open(server.url());
    }
}
