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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The xsdviewer.ini reader, and that a command-line option still overrides what it holds. */
class ConfigFileTest {

    private Path ini(Path dir, String content) throws IOException {
        return Files.writeString(dir.resolve(ConfigFile.NAME), content);
    }

    @Test
    void readsHostAndPort(@TempDir Path dir) throws IOException {
        ConfigFile c = ConfigFile.read(ini(dir, "# a comment\nport = 9091\nhost=0.0.0.0\n; another comment\n"));
        assertEquals("0.0.0.0", c.host());
        assertEquals(9091, c.port());
    }

    @Test
    void unsetKeysKeepTheBuiltInDefaults(@TempDir Path dir) throws IOException {
        ConfigFile c = ConfigFile.read(ini(dir, "port=9000\n"));
        assertEquals(CommandLineOptions.DEFAULT_HOST, c.host());   // host not set: the default
        assertEquals(9000, c.port());
    }

    @Test
    void aBadPortKeepsTheDefault(@TempDir Path dir) throws IOException {
        ConfigFile c = ConfigFile.read(ini(dir, "port=not-a-number\n"));
        assertEquals(CommandLineOptions.DEFAULT_PORT, c.port());
    }

    @Test
    void theCommandLineOverridesTheFile() {
        // the file's values are the parse defaults; --port on the command line wins over them
        CommandLineOptions fromFileOnly = CommandLineOptions.parse(new String[0], "0.0.0.0", 9091);
        assertEquals("0.0.0.0", fromFileOnly.host());
        assertEquals(9091, fromFileOnly.port());

        CommandLineOptions overridden = CommandLineOptions.parse(new String[] { "--port", "9090" }, "0.0.0.0", 9091);
        assertEquals(9090, overridden.port());
        assertEquals("0.0.0.0", overridden.host());   // host not on the command line: the file's value stands
    }
}
