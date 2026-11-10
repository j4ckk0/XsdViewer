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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The tool's log, as the code says it: {@code debug}, {@code info}, {@code warn}. The logger is
 * {@code org.jtools.xsdviewer}, which JToolsFramework's {@code WebApp} sets up with the rest of the program's — the
 * console, the file under {@code ~/.xsdviewer/logs}, the level from {@code --log-level}, {@code --verbose} or the
 * {@code .ini}'s {@code log.level}.
 */
public final class Log {

    private static final Logger LOGGER = Logger.getLogger("org.jtools.xsdviewer");

    private Log() {}

    /** What only a verbose log keeps: each parse, each request. */
    public static void debug(String message) {
        LOGGER.fine(message);
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warning(message);
    }

    public static void warn(String message, Throwable cause) {
        LOGGER.log(Level.WARNING, message, cause);
    }
}
