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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** The tool's log: what happens and what fails, on the console and in a rotating file of the temporary directory (a launcher without console still leaves a trace). */
public final class Log {

    private static final String NAME = "xsdviewer";
    private static final String FILE_PATTERN = "%t/xsdviewer.%g.log";
    private static final int FILE_LIMIT = 1_000_000, FILE_COUNT = 2;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Logger LOGGER = Logger.getLogger(NAME);
    private static Path file;

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.INFO);
        addHandler(new ConsoleHandler());
        try {
            FileHandler handler = new FileHandler(FILE_PATTERN, FILE_LIMIT, FILE_COUNT, true);
            addHandler(handler);
            file = Path.of(System.getProperty("java.io.tmpdir"), "xsdviewer.0.log");
        } catch (IOException | SecurityException e) {
            LOGGER.warning(Messages.get(MessageKey.LOG_FILE_UNAVAILABLE, e.getMessage()));
        }
    }

    private Log() {}

    private static void addHandler(Handler handler) {
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord r) {
                StringBuilder sb = new StringBuilder(TIME.format(LocalDateTime.now())).append(' ').append(r.getLevel()).append(' ').append(formatMessage(r)).append('\n');
                if (r.getThrown() != null) {
                    StringWriter sw = new StringWriter();
                    r.getThrown().printStackTrace(new PrintWriter(sw));
                    sb.append(sw);
                }
                return sb.toString();
            }
        });
        LOGGER.addHandler(handler);
    }

    /** The log file, or null when none could be opened. */
    public static Path file() {
        return file;
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warning(message);
    }

    public static void warn(String message, Throwable t) {
        LOGGER.log(Level.WARNING, message, t);
    }
}
