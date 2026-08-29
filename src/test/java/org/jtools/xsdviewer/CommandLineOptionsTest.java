package org.jtools.xsdviewer;

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
