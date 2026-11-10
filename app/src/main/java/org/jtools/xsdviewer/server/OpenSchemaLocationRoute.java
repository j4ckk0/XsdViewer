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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;

import org.jtools.framework.server.Exchange;
import org.jtools.framework.server.WebServer;

/**
 * {@code GET /api/open?base=&location=}: the schema an xs:import / xs:include points to, resolved
 * against the referencing file's directory — then, unless {@code strict}, the other served
 * directories and the working directory. Remote locations are refused: the tool never goes on the network.
 */
final class OpenSchemaLocationRoute implements WebServer.Route {

    private static final String REMOTE_LOCATION_MARK = "://";

    private final ServedSchemaFiles files;

    OpenSchemaLocationRoute(ServedSchemaFiles files) {
        this.files = files;
    }

    @Override
    public Object answer(Exchange ex) throws IOException {
        String base = ex.query(ApiPath.PARAM_BASE, ""), location = ex.query(ApiPath.PARAM_LOCATION, "");
        if (location.isEmpty()) throw new WebServer.Refusal(HttpStatus.BAD_REQUEST, Messages.get(MessageKey.LOCATION_EXPECTED));
        if (location.contains(REMOTE_LOCATION_MARK)) {
            throw new WebServer.Refusal(HttpStatus.BAD_REQUEST, Messages.get(MessageKey.REMOTE_LOCATION_NOT_SUPPORTED, location));
        }
        String rel = location.replace('\\', '/');
        boolean strict = ApiPath.TRUE.equals(ex.query(ApiPath.PARAM_STRICT, ""));
        for (Path dir : searchDirectories(base, strict)) {
            Path target = dir.resolve(rel).normalize();
            if (Files.isRegularFile(target)) return files.answer(target);
        }
        throw new WebServer.Refusal(HttpStatus.NOT_FOUND, Messages.get(MessageKey.FILE_NOT_FOUND, location));
    }

    private List<Path> searchDirectories(String base, boolean strict) {
        List<Path> dirs = new ArrayList<>();
        if (!base.isEmpty()) {
            Path basePath = Path.of(base).toAbsolutePath().normalize();
            if (files.contains(basePath) && basePath.getParent() != null) dirs.add(basePath.getParent());
        }
        if (strict) return dirs;
        dirs.addAll(files.directories());
        dirs.add(Path.of("").toAbsolutePath());
        return dirs;
    }
}
