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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LogTest {

    private final List<LogRecord> seen = new ArrayList<>();
    private final Handler catching = new Handler() {
        @Override public void publish(LogRecord r) { seen.add(r); }
        @Override public void flush() {}
        @Override public void close() {}
    };

    @AfterEach
    void quiet() {
        Logger.getLogger("xsdviewer").removeHandler(catching);
        Log.setVerbose(false);
    }

    @Test
    void debugRecordsAreWrittenOnlyWhenVerbose() {
        Logger logger = Logger.getLogger("xsdviewer");
        catching.setLevel(Level.ALL);
        logger.addHandler(catching);
        Log.setVerbose(false);
        assertFalse(Log.isVerbose());
        Log.debug("quiet");
        Log.info("said");
        assertEquals(List.of("said"), seen.stream().map(LogRecord::getMessage).toList());
        Log.setVerbose(true);
        assertTrue(Log.isVerbose());
        Log.debug("told");
        assertEquals(List.of("said", "told"), seen.stream().map(LogRecord::getMessage).toList());
    }
}
