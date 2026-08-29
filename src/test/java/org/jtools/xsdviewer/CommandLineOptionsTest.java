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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CommandLineOptionsTest {

    @Test
    void defaults() {
        CommandLineOptions o = CommandLineOptions.parse(new String[0]);
        assertEquals(CommandLineOptions.DEFAULT_HOST, o.host());
        assertEquals(CommandLineOptions.DEFAULT_PORT, o.port());
        assertTrue(o.openBrowser());
        assertFalse(o.help());
        assertNull(o.initialFile());
    }

    @Test
    void allOptions() {
        CommandLineOptions o = CommandLineOptions.parse(new String[] { "--port", "9090", "--host", "0.0.0.0", "--no-browser", "a.xsd" });
        assertEquals("0.0.0.0", o.host());
        assertEquals(9090, o.port());
        assertFalse(o.openBrowser());
        assertEquals(Path.of("a.xsd"), o.initialFile());
    }

    @Test
    void help() {
        assertTrue(CommandLineOptions.parse(new String[] { "-h" }).help());
        assertTrue(CommandLineOptions.parse(new String[] { "--help" }).help());
    }

    @Test
    void badValues() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CommandLineOptions.parse(new String[] { "--port" }));
        assertEquals(Messages.get(MessageKey.OPTION_VALUE_EXPECTED, "--port"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> CommandLineOptions.parse(new String[] { "--port", "abc" }));
        assertEquals(Messages.get(MessageKey.INVALID_PORT, "abc"), e.getMessage());
    }
}
