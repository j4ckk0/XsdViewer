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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The schema files of a folder and its sub-folders (bounded walk, hidden directories skipped), sorted by path. */
final class SchemaFolder {

    static final int MAX_DEPTH = 8;
    /** Files beyond this many are left out ({@link Listing#truncated}). */
    static final int MAX_FILES = 200;
    private static final String SCHEMA_EXTENSION = ".xsd";
    private static final String HIDDEN_PREFIX = ".";

    private SchemaFolder() {}

    record Listing(List<Path> files, boolean truncated) {}

    static Listing list(Path folder) throws IOException {
        List<Path> all = new ArrayList<>();
        try (var walk = Files.walk(folder, MAX_DEPTH)) {
            walk.filter(p -> Files.isRegularFile(p) && isSchema(p) && !hidden(folder, p)).forEach(all::add);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        all.sort(null);
        boolean truncated = all.size() > MAX_FILES;
        return new Listing(truncated ? all.subList(0, MAX_FILES) : all, truncated);
    }

    private static boolean isSchema(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SCHEMA_EXTENSION);
    }

    private static boolean hidden(Path root, Path p) {
        for (Path part : root.relativize(p)) if (part.toString().startsWith(HIDDEN_PREFIX)) return true;
        return false;
    }
}
