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
import java.nio.file.Files;
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

/**
 * The tool's log: what happens and what fails, on the console and, once {@link #openFile a folder is
 * chosen}, in a pair of rotating files there (a launcher without console still leaves a trace). Two
 * levels: what happens to the server and what fails, always; each request and each parse besides,
 * when {@link #setVerbose verbose}. Both are set at start-up from {@code xsdviewer.ini} and the
 * command line; until then the console alone receives the records.
 */
public final class Log {

    private static final String NAME = "xsdviewer";
    /** The rotating files: {@code %g} is the generation, 0 the one being written. */
    private static final String FILE_PATTERN = "xsdviewer.%g.log";
    private static final String CURRENT_FILE = "xsdviewer.0.log";
    private static final int FILE_LIMIT = 1_000_000, FILE_COUNT = 2;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Logger LOGGER = Logger.getLogger(NAME);
    private static FileHandler fileHandler;
    private static Path file;

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.INFO);
        addHandler(new ConsoleHandler());
    }

    private Log() {}

    /** Where the files go unless {@code xsdviewer.ini} says otherwise: the temporary directory. */
    public static Path defaultFolder() {
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Writes the log to {@code xsdviewer.0.log} in {@code folder} (created if needed) besides the
     * console; null closes the file and keeps the console alone. A folder that cannot be written
     * is reported on the console and the log goes on without a file.
     */
    public static synchronized void openFile(Path folder) {
        if (fileHandler != null) {
            LOGGER.removeHandler(fileHandler);
            fileHandler.close();
            fileHandler = null;
            file = null;
        }
        if (folder == null) {
            return;
        }
        try {
            Files.createDirectories(folder);
            String pattern = folder.toString().replace("%", "%%") + "/" + FILE_PATTERN;   // "%" is FileHandler's escape, "/" its separator on every platform
            fileHandler = new FileHandler(pattern, FILE_LIMIT, FILE_COUNT, true);
            fileHandler.setLevel(LOGGER.getLevel());
            addHandler(fileHandler);
            file = folder.toAbsolutePath().resolve(CURRENT_FILE);   // absolute: the About dialog shows it
        } catch (IOException | SecurityException e) {
            LOGGER.warning(Messages.get(MessageKey.LOG_FILE_UNAVAILABLE, e.getMessage()));
        }
    }

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

    /** Verbose: the {@code FINE} records — a request, a parse — reach the console and the file too; otherwise {@code INFO} and up. */
    public static void setVerbose(boolean verbose) {
        Level level = verbose ? Level.FINE : Level.INFO;
        LOGGER.setLevel(level);
        for (Handler h : LOGGER.getHandlers()) h.setLevel(level);
    }

    public static boolean isVerbose() {
        return LOGGER.getLevel().intValue() <= Level.FINE.intValue();
    }

    /** What a developer following the tool wants to see: written only when verbose. */
    public static void debug(String message) {
        LOGGER.fine(message);
    }

    /** The file being written, or null when there is none: not {@link #openFile opened} yet, disabled, or unwritable. */
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
