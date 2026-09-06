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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The optional {@code xsdviewer.ini} read at start-up, so the host and the port can be set without a
 * command line — for the double-clicked launcher, which passes none. The file is looked for in the
 * working directory (where the launcher runs), then beside the jar; a {@code key=value} line per
 * setting, a line starting with {@code #} or {@code ;} a comment. Command-line options override it.
 */
public final class ConfigFile {

    public static final String NAME = "xsdviewer.ini";
    public static final String KEY_HOST = "host", KEY_PORT = "port";

    private final String host;
    private final int port;

    private ConfigFile(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    /** The configuration found near the running program, the built-in defaults where the file is absent or a key unset. */
    public static ConfigFile load() {
        Path file = locate();
        return file == null ? defaults() : read(file);
    }

    /** The built-in defaults, when no file overrides them. */
    public static ConfigFile defaults() {
        return new ConfigFile(CommandLineOptions.DEFAULT_HOST, CommandLineOptions.DEFAULT_PORT);
    }

    /** The configuration read from {@code file}: the built-in defaults for every key it does not set. */
    public static ConfigFile read(Path file) {
        String host = CommandLineOptions.DEFAULT_HOST;
        int port = CommandLineOptions.DEFAULT_PORT;
        for (String line : lines(file)) {
            String setting = line.strip();
            if (setting.isEmpty() || setting.startsWith("#") || setting.startsWith(";")) {
                continue;
            }
            int equals = setting.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = setting.substring(0, equals).strip().toLowerCase(Locale.ROOT);
            String value = setting.substring(equals + 1).strip();
            if (key.equals(KEY_HOST)) {
                host = value;
            } else if (key.equals(KEY_PORT)) {
                try {
                    port = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    Log.warn(Messages.get(MessageKey.INVALID_PORT, value));   // a bad port in the file: warn and keep the default
                }
            }
        }
        return new ConfigFile(host, port);
    }

    /** The file in the working directory, else beside the jar, else null. */
    private static Path locate() {
        Path inWorkingDir = Path.of(NAME);
        if (Files.isRegularFile(inWorkingDir)) {
            return inWorkingDir;
        }
        Path besideJar = besideJar();
        return besideJar != null && Files.isRegularFile(besideJar) ? besideJar : null;
    }

    private static Path besideJar() {
        try {
            Path jar = Path.of(ConfigFile.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path directory = Files.isDirectory(jar) ? jar : jar.getParent();
            return directory == null ? null : directory.resolve(NAME);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            Log.warn("cannot read " + file + ": " + e.getMessage());
            return List.of();
        }
    }
}
