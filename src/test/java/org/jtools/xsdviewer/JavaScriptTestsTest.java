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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Runs the tests of the page ({@code src/test/js/*.test.mjs}, Node's own test runner) from Maven.
 * Node is looked for as the {@code node} system property, the {@code NODE} environment variable,
 * then on the PATH; without it the test is skipped with a note (the CI workflow installs Node).
 */
class JavaScriptTestsTest {

    /** A pattern (Node expands it itself): a directory would be taken for a file. */
    private static final String TESTS = "src/test/js/*.test.mjs";
    private static final String NODE = "node";
    private static final String TEST_FLAG = "--test";
    private static final long TIMEOUT_SECONDS = 120;

    @Test
    void pageTestsPass() throws Exception {
        String node = findNode();
        assumeTrue(node != null, "Node.js not found: the tests of the page (src/test/js) are skipped - install Node or set NODE");
        Process p = new ProcessBuilder(node, TEST_FLAG, TESTS).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean done = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!done) p.destroyForcibly();
        System.out.println(out);
        assertEquals(0, done ? p.exitValue() : -1, "node --test " + TESTS + " failed:\n" + out);
    }

    private static String findNode() throws IOException {
        for (String candidate : List.of(System.getProperty(NODE, ""), System.getenv().getOrDefault("NODE", ""))) {
            if (!candidate.isEmpty() && Files.isExecutable(Path.of(candidate))) return candidate;
        }
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            Path p = Path.of(dir, NODE);
            if (Files.isExecutable(p)) return p.toString();
        }
        return null;
    }
}
