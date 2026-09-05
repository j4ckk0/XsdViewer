package org.jtools.xsdviewer.compare;

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
import java.util.Map;

import org.jtools.xsdviewer.compare.BusinessLines.Line;
import org.jtools.xsdviewer.compare.LineDiff.Op;
import org.jtools.xsdviewer.compare.ModelDiff.Counts;
import org.jtools.xsdviewer.compare.SchemaDiff.Link;
import org.jtools.xsdviewer.compare.WorkspacePairing.Pair;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.jtools.xsdviewer.schema.SchemaGraph.Edge;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;

/**
 * The answers of {@link org.jtools.xsdviewer.compare} as the JSON the page and the HTTP API speak
 * (keys: {@link JsonKey}). The comparisons themselves know nothing of JSON; this is the one place
 * that shapes them, as {@link org.jtools.xsdviewer.schema.SchemaGraphJsonWriter} does for a graph.
 */
public final class CompareJsonWriter {

    private CompareJsonWriter() {}

    /** Two texts compared: the lines of each side with their number, the edit script, whether only blocks moved. */
    public static void texts(JsonWriter w, TextComparison.Result r) {
        w.beginObject().name(JsonKey.LA);
        lines(w, r.la());
        w.name(JsonKey.LB);
        lines(w, r.lb());
        w.name(JsonKey.OPS);
        if (r.ops() == null) {
            w.nullValue();   // too different to align: the page says so rather than drawing nothing
        } else {
            w.beginArray();
            for (Op o : r.ops()) op(w, o);
            w.endArray();
        }
        w.property(JsonKey.ONLY_MOVES, r.onlyMoves()).endObject();
    }

    /** What two schemas declare and link that the other does not; {@code schemas} false when either text is not a schema. */
    public static void schemas(JsonWriter w, SchemaDiff.Result d) {
        w.beginObject().property(JsonKey.SCHEMAS, true).property(JsonKey.SAME, d.same());
        nodes(w.name(JsonKey.NODES_ONLY_LEFT), d.nodesOnlyLeft());
        nodes(w.name(JsonKey.NODES_ONLY_RIGHT), d.nodesOnlyRight());
        edges(w.name(JsonKey.EDGES_ONLY_LEFT), d.edgesOnlyLeft());
        edges(w.name(JsonKey.EDGES_ONLY_RIGHT), d.edgesOnlyRight());
        w.endObject();
    }

    /** How many boxes of each mark two content models hold. */
    public static void counts(JsonWriter w, Counts counts) {
        w.beginObject().property(ModelDiff.SAME, counts.same).property(ModelDiff.CHANGED, counts.changed)
                .property(ModelDiff.REMOVED, counts.removed).property(ModelDiff.ADDED, counts.added).endObject();
    }

    /** The links of each neighbourhood the other side does not have. */
    public static void links(JsonWriter w, Map<String, Link> left, Map<String, Link> right) {
        w.beginObject().name(JsonKey.ONLY_LEFT).beginArray();
        for (Map.Entry<String, Link> e : left.entrySet()) if (!right.containsKey(e.getKey())) link(w, e.getValue());
        w.endArray().name(JsonKey.ONLY_RIGHT).beginArray();
        for (Map.Entry<String, Link> e : right.entrySet()) if (!left.containsKey(e.getKey())) link(w, e.getValue());
        w.endArray().endObject();
    }

    /** The files of two workspaces paired by name, each with its status. */
    public static void pairs(JsonWriter w, List<Pair> pairs) {
        w.beginObject().name(JsonKey.PAIRS).beginArray();
        for (Pair p : pairs) w.beginObject().property(JsonKey.NAME, p.name()).property(JsonKey.STATUS, p.status()).endObject();
        w.endArray().endObject();
    }

    private static void lines(JsonWriter w, List<Line> lines) {
        w.beginArray();
        for (Line l : lines) w.beginObject().property(JsonKey.N, l.n()).property(JsonKey.TEXT, l.text()).endObject();
        w.endArray();
    }

    private static void op(JsonWriter w, Op o) {
        w.beginObject().property(JsonKey.OP, String.valueOf(o.op));
        if (o.a >= 0) w.property(JsonKey.A, o.a);
        if (o.b >= 0) w.property(JsonKey.B, o.b);
        if (o.moved) {
            w.property(JsonKey.MOVED, true);
            if (o.movedTo >= 0) w.property(JsonKey.MOVED_TO, o.movedTo);
            if (o.movedFrom >= 0) w.property(JsonKey.MOVED_FROM, o.movedFrom);
        }
        w.endObject();
    }

    private static void link(JsonWriter w, Link link) {
        w.beginObject().property(JsonKey.LABEL, link.label()).property(JsonKey.KIND, link.kind()).property(JsonKey.NAME, link.name());
        cardinality(w, link.cardinality());
        w.endObject();
    }

    private static void nodes(JsonWriter w, List<Node> nodes) {
        w.beginArray();
        for (Node n : nodes) w.beginObject().property(JsonKey.ID, n.id()).property(JsonKey.KIND, n.kind()).property(JsonKey.NAME, n.name()).endObject();
        w.endArray();
    }

    private static void edges(JsonWriter w, List<Edge> edges) {
        w.beginArray();
        for (Edge e : edges) {
            w.beginObject().property(JsonKey.FROM, e.from()).property(JsonKey.TO, e.to()).property(JsonKey.LABEL, e.label());
            cardinality(w, e.cardinality());
            w.endObject();
        }
        w.endArray();
    }

    /** The occurrences of a link, written only when it has some (a type link has none). */
    private static void cardinality(JsonWriter w, Cardinality c) {
        if (c != null) w.property(JsonKey.MIN, c.min()).property(JsonKey.MAX, c.max());
    }
}
