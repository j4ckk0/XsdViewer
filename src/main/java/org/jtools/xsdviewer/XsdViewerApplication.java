package org.jtools.xsdviewer;

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

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.JOptionPane;

import org.jtools.xsdviewer.server.XsdViewerServer;

/**
 * Entry point: reads the command line, starts the {@link XsdViewerServer} and opens the browser.
 *
 * <pre>
 *   java -jar xsdviewer.jar [--port N] [--host H] [--no-browser] [--keep-alive] [file.xsd]
 * </pre>
 */
public final class XsdViewerApplication {

    public static final String APP_NAME = "XsdViewer";

    private static final int EXIT_BAD_FILE = 1;
    private static final int EXIT_BAD_USAGE = 2;
    private static final int EXIT_CANNOT_START = 3;

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

        XsdViewerServer server;
        try {
            server = XsdViewerServer.start(options.host(), options.port(), options.initialFile(), options.stopWhenNoPage() && UserSettings.autoStop());
        } catch (IOException e) {
            String message = Messages.get(MessageKey.CANNOT_START, options.host(), String.valueOf(options.port()), e.getMessage());
            Log.warn(message, e);
            reportWithoutConsole(message);
            System.exit(EXIT_CANNOT_START);
            return;
        }
        Log.info(Messages.get(MessageKey.SERVER_LISTENING, server.url()));
        if (Log.file() != null) Log.info(Messages.get(MessageKey.LOG_FILE, Log.file()));
        if (options.openBrowser()) BrowserLauncher.open(server.url());
    }

    /** Started without a console (javaw, a double-clicked launcher): the message would be lost, so show it in a dialog. */
    private static void reportWithoutConsole(String message) {
        if (System.console() != null || GraphicsEnvironment.isHeadless()) return;
        try {
            JOptionPane.showMessageDialog(null, message, APP_NAME, JOptionPane.ERROR_MESSAGE);
        } catch (Exception ignored) {
            // no usable display after all: the message went to stderr
        }
    }
}
