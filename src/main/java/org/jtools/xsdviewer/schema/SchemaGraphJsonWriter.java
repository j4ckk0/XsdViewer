package org.jtools.xsdviewer.schema;

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
                    .property(JsonKey.DOC, n.doc())
                    .endObject();
        }
        w.endArray();
        w.name(JsonKey.EDGES).beginArray();
        for (SchemaGraph.Edge e : g.edges) {
            w.beginObject()
                    .property(JsonKey.FROM, e.from())
                    .property(JsonKey.TO, e.to())
                    .property(JsonKey.LABEL, e.label())
                    .endObject();
        }
        w.endArray();
        return w.endObject().toString();
    }
}
