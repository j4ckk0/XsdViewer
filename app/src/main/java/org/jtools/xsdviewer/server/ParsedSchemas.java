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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jtools.xsdviewer.schema.SchemaException;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.SchemaParser;

/**
 * The graphs of the texts the requests carry, kept a while by the hash of each text. The model and
 * comparison requests are stateless — the same files come back with every click on a box — so
 * without this the workspace would be parsed again at each one.
 */
final class ParsedSchemas {

    /** How many texts are kept: a workspace's files several times over, the least recently used giving way. */
    private static final int SIZE = 500;
    private static final String HASH = "SHA-256";

    private static final Map<String, SchemaGraph> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SchemaGraph> eldest) {
            return size() > SIZE;
        }
    };

    private ParsedSchemas() {}

    /** The graph of a text, from the cache or parsed now. */
    static SchemaGraph of(String text) throws SchemaException {
        String key = hash(text);
        synchronized (CACHE) {
            SchemaGraph cached = CACHE.get(key);   // an access-order map: reading it is a change, hence the lock
            if (cached != null) return cached;
        }
        SchemaGraph graph = SchemaParser.parse(text);
        synchronized (CACHE) {
            CACHE.put(key, graph);
        }
        return graph;
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH).digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256 is in every JDK
        }
    }
}
