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

import java.nio.file.Path;

/**
 * The options accepted on the command line.
 *
 * @param host        interface the server binds to
 * @param port        port the server listens on
 * @param openBrowser whether to open the page in the default browser at start-up
 * @param keepAlive   keep the server running once every page has been closed ({@code --keep-alive}; also implied by {@code --no-browser})
 * @param help        {@code -h} / {@code --help} was given: print the usage and stop
 * @param verbose     {@code --verbose}: the log tells each request and each parse, not only what happens to the server and what fails
 * @param initialFile schema to open at start-up, or null
 */
public record CommandLineOptions(String host, int port, boolean openBrowser, boolean keepAlive, boolean help, boolean verbose, Path initialFile) {

    public static final String OPTION_PORT = "--port";
    public static final String OPTION_HOST = "--host";
    public static final String OPTION_NO_BROWSER = "--no-browser";
    public static final String OPTION_KEEP_ALIVE = "--keep-alive";
    public static final String OPTION_HELP = "--help";
    public static final String OPTION_VERBOSE = "--verbose";
    public static final String OPTION_HELP_SHORT = "-h";

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8080;

    /** @throws IllegalArgumentException when an option lacks its value or the port is not a number */
    public static CommandLineOptions parse(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        boolean openBrowser = true, keepAlive = false, help = false, verbose = false;
        Path initialFile = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case OPTION_PORT -> port = parsePort(valueOf(args, i++));
                case OPTION_HOST -> host = valueOf(args, i++);
                case OPTION_NO_BROWSER -> openBrowser = false;
                case OPTION_KEEP_ALIVE -> keepAlive = true;
                case OPTION_HELP, OPTION_HELP_SHORT -> help = true;
                case OPTION_VERBOSE -> verbose = true;
                default -> initialFile = Path.of(args[i]);
            }
        }
        return new CommandLineOptions(host, port, openBrowser, keepAlive, help, verbose, initialFile);
    }

    /** Whether the server stops by itself once every page has been closed: not with {@code --keep-alive}, nor with {@code --no-browser} (the page will be opened later, by hand). */
    public boolean stopWhenNoPage() {
        return !keepAlive && openBrowser;
    }

    /** The value following the option at {@code i}. */
    private static String valueOf(String[] args, int i) {
        if (i + 1 >= args.length) {
            throw new IllegalArgumentException(Messages.get(MessageKey.OPTION_VALUE_EXPECTED, args[i]));
        }
        return args[i + 1];
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(Messages.get(MessageKey.INVALID_PORT, value));
        }
    }
}
