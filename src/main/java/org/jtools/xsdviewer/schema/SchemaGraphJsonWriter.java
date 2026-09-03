package org.jtools.xsdviewer.schema;

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

import java.util.List;

import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/** Serialises a {@link SchemaGraph} to the JSON the page expects (keys: {@link JsonKey}). */
public final class SchemaGraphJsonWriter {

    private static final int INITIAL_CAPACITY = 4096;

    private SchemaGraphJsonWriter() {}

    public static String write(SchemaGraph g) {
        JsonWriter w = new JsonWriter(INITIAL_CAPACITY).beginObject();
        w.property(JsonKey.TARGET_NAMESPACE, g.targetNamespace);
        w.name(JsonKey.IMPORTS).beginArray();
        for (SchemaGraph.Import i : g.imports) {
            w.beginObject()
                    .property(JsonKey.TAG, i.tag())
                    .property(JsonKey.NAMESPACE, i.namespace())
                    .property(JsonKey.SCHEMA_LOCATION, i.schemaLocation())
                    .endObject();
        }
        w.endArray();
        w.name(JsonKey.NODES).beginArray();
        for (SchemaGraph.Node n : g.nodes.values()) {
            w.beginObject()
                    .property(JsonKey.ID, n.id())
                    .property(JsonKey.KIND, n.kind())
                    .property(JsonKey.NAME, n.name())
                    .property(JsonKey.NS, n.ns())
                    .property(JsonKey.LINE, n.line())
                    .property(JsonKey.DOC, n.doc());
            if (!n.xpath().isEmpty()) w.property(JsonKey.XPATH, n.xpath());
            if (!n.members().isEmpty()) {
                w.name(JsonKey.MEMBERS).beginArray();
                for (String m : n.members()) w.value(m);
                w.endArray();
            }
            if (!n.values().isEmpty()) {
                w.name(JsonKey.VALUES).beginArray();
                for (SchemaGraph.Value v : n.values()) {
                    w.beginObject().property(JsonKey.VALUE, v.value()).property(JsonKey.DOC, v.doc()).endObject();
                }
                w.endArray();
            }
            content(w, n.content(), n.attributes());
            w.endObject();
        }
        w.endArray();
        w.name(JsonKey.EDGES).beginArray();
        for (SchemaGraph.Edge e : g.edges) {
            w.beginObject()
                    .property(JsonKey.FROM, e.from())
                    .property(JsonKey.TO, e.to())
                    .property(JsonKey.LABEL, e.label());
            if (e.cardinality() != null) w.property(JsonKey.MIN, e.cardinality().min()).property(JsonKey.MAX, e.cardinality().max());
            if (!e.compositor().isEmpty()) w.property(JsonKey.COMPOSITOR, e.compositor());
            w.endObject();
        }
        w.endArray();
        return w.endObject().toString();
    }

    /** The content model of a node or of a particle's anonymous type: {@code content} and {@code attributes}, each only when there is something. */
    private static void content(JsonWriter w, List<SchemaGraph.Particle> particles, List<SchemaGraph.Attribute> attributes) {
        if (!particles.isEmpty()) {
            w.name(JsonKey.CONTENT).beginArray();
            for (SchemaGraph.Particle p : particles) particle(w, p);
            w.endArray();
        }
        if (!attributes.isEmpty()) {
            w.name(JsonKey.ATTRIBUTES).beginArray();
            for (SchemaGraph.Attribute a : attributes) {
                w.beginObject().property(JsonKey.NAME, a.name());
                optional(w, JsonKey.REF, a.ref());
                optional(w, JsonKey.TYPE, a.type());
                if (a.use() != null) w.property(JsonKey.MIN, a.use().min()).property(JsonKey.MAX, a.use().max());
                w.endObject();
            }
            w.endArray();
        }
    }

    private static void particle(JsonWriter w, SchemaGraph.Particle p) {
        w.beginObject().property(JsonKey.KIND, p.kind());
        optional(w, JsonKey.NAME, p.name());
        optional(w, JsonKey.REF, p.ref());
        optional(w, JsonKey.TYPE, p.type());
        optional(w, JsonKey.NAMESPACE, p.namespace());
        if (p.cardinality() != null) w.property(JsonKey.MIN, p.cardinality().min()).property(JsonKey.MAX, p.cardinality().max());
        if (!p.children().isEmpty()) {
            w.name(JsonKey.CHILDREN).beginArray();
            for (SchemaGraph.Particle c : p.children()) particle(w, c);
            w.endArray();
        }
        content(w, List.of(), p.attributes());
        w.endObject();
    }

    private static void optional(JsonWriter w, String key, String value) {
        if (!value.isEmpty()) w.property(key, value);
    }
}
