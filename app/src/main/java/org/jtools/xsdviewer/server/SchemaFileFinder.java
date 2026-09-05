package org.jtools.xsdviewer.server;

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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Finds a file under a directory by name and content: how the server locates a file the browser opened without telling its folder. Bounded walk. */
final class SchemaFileFinder {

    private static final int MAX_DEPTH = 8;
    private static final int MAX_ENTRIES = 50_000;
    private static final String BOM = "\uFEFF";

    /** A regular file named {@code name} whose canonical content is {@code wanted}, under {@code root}; null when none. */
    Path find(Path root, String name, String wanted) {
        int[] budget = { MAX_ENTRIES };
        try (var walk = Files.walk(root, MAX_DEPTH)) {
            var it = walk.filter(p -> {
                if (--budget[0] < 0) return false;
                return p.getFileName() != null && p.getFileName().toString().equals(name)
                        && !SchemaFolder.hidden(root, p) && Files.isRegularFile(p);
            }).iterator();
            while (it.hasNext()) {
                Path p = it.next();
                try {
                    if (canonical(SchemaText.read(p)).equals(wanted)) return p.toAbsolutePath().normalize();
                } catch (IOException | UncheckedIOException e) { /* unreadable: not this one */ }
            }
        } catch (IOException | UncheckedIOException e) { /* unreadable directory */ }
        return null;
    }

    /** Text without BOM and with LF line endings, so that a file compares equal however it was read. */
    static String canonical(String text) {
        if (text.startsWith(BOM)) text = text.substring(BOM.length());
        return text.replace("\r\n", "\n");
    }
}
