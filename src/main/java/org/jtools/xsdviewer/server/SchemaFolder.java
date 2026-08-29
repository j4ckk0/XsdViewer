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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.EnumSet;
import java.nio.file.FileVisitOption;

import org.jtools.xsdviewer.Log;

/** The schema files of a folder and its whole sub-tree (symbolic links followed, hidden and unreadable directories skipped), sorted by path. */
final class SchemaFolder {

    static final int MAX_DEPTH = 64;
    /** Files beyond this many are left out ({@link Listing#truncated}). */
    static final int MAX_FILES = 2000;
    private static final String SCHEMA_EXTENSION = ".xsd";
    private static final String HIDDEN_PREFIX = ".";

    private SchemaFolder() {}

    record Listing(List<Path> files, boolean truncated) {}

    static Listing list(Path folder) throws IOException {
        List<Path> all = new ArrayList<>();
        Files.walkFileTree(folder, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_DEPTH, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return dir.equals(folder) || !hidden(folder, dir) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && isSchema(file) && !hidden(folder, file)) all.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                Log.warn(file + ": " + e);   // unreadable, or a link loop: skipped, the rest of the folder is still listed
                return FileVisitResult.CONTINUE;
            }
        });
        all.sort(null);
        boolean truncated = all.size() > MAX_FILES;
        return new Listing(truncated ? all.subList(0, MAX_FILES) : all, truncated);
    }

    private static boolean isSchema(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SCHEMA_EXTENSION);
    }

    /** True when {@code p}, under {@code root}, lies in a hidden directory (a dot-name) or is one itself. */
    static boolean hidden(Path root, Path p) {
        for (Path part : root.relativize(p)) if (part.toString().startsWith(HIDDEN_PREFIX)) return true;
        return false;
    }
}
