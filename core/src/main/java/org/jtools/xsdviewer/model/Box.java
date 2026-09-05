package org.jtools.xsdviewer.model;

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

import java.util.ArrayList;
import java.util.List;

import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;

/**
 * One box of a content model as the page draws it: the declaration at the root, then a compositor, an
 * element, an attribute, a group reference, a wildcard, a base type, or — for a WSDL's or a
 * Schematron's own object — what a link of its chain leads to, the link's {@code word} above its name.
 * {@code path} is its place in the tree (the indexes from the root, {@code /0/a1/2}), what the page
 * names an opened box by; {@code diff} and {@code foldKey} are set once two models are compared.
 */
public final class Box {

    public final String kind;
    public final String name;
    /** The node id of a declaration box (the root); null for a box of a content model or a chain. */
    public final String id;
    public final String path;
    /** The node id the box refers to: a global element, a group, a chain's target; empty otherwise. */
    public String ref = "";
    /** The node id of the type the box is of, and that type's name (the id's name when it is declared nowhere). */
    public String typeId = "";
    public String typeName = "";
    /** The word of the link a chain box stands for ({@code operation}, {@code input}, a port's name); empty otherwise. */
    public String word = "";
    /** A wildcard's namespace constraint. */
    public String namespace = "";
    /** The box's own occurrences (an element's, a compositor's, an attribute's use); null when it has none (the root, a base type). */
    public Cardinality card;
    /** Whether the box stands for something with content of its own, whether that content is shown, whether it is already open above. */
    public boolean expandable, expanded, recursive;
    public final boolean root;
    public final List<Box> attributes = new ArrayList<>();
    public final List<Box> children = new ArrayList<>();
    /** How the box differs from the one matching it on the other side ({@link org.jtools.xsdviewer.compare.ModelDiff}), and the trail naming the pair. */
    public String diff, foldKey;

    Box(String kind, String name, String id, String path, boolean root) {
        this.kind = kind;
        this.name = name;
        this.id = id;
        this.path = path;
        this.root = root;
    }

    /** The JSON the page draws from: every field it reads, the flags only when set, the occurrences only when there are some. */
    public void write(JsonWriter w) {
        w.beginObject().property(JsonKey.KIND, kind).property(JsonKey.NAME, name);
        if (id != null) w.property(JsonKey.ID, id);
        w.property(JsonKey.PATH, path).property(JsonKey.REF, ref).property(JsonKey.TYPE_ID, typeId).property(JsonKey.TYPE_NAME, typeName)
                .property(JsonKey.WORD, word).property(JsonKey.NAMESPACE, namespace);
        if (card != null) {
            w.name(JsonKey.CARD).beginObject().property(JsonKey.MIN, card.min()).property(JsonKey.MAX, card.max()).endObject();
        }
        if (expandable) w.property(JsonKey.EXPANDABLE, true);
        if (expanded) w.property(JsonKey.EXPANDED, true);
        if (recursive) w.property(JsonKey.RECURSIVE, true);
        if (root) w.property(JsonKey.ROOT, true);
        if (diff != null) w.property(JsonKey.DIFF, diff).property(JsonKey.FOLD_KEY, foldKey);
        w.name(JsonKey.ATTRIBUTES).beginArray();
        for (Box a : attributes) a.write(w);
        w.endArray().name(JsonKey.CHILDREN).beginArray();
        for (Box c : children) c.write(w);
        w.endArray().endObject();
    }

    /** This box and every box under it, the attributes of each before its children. */
    public List<Box> all() {
        List<Box> out = new ArrayList<>();
        collect(out);
        return out;
    }

    private void collect(List<Box> out) {
        out.add(this);
        for (Box a : attributes) a.collect(out);
        for (Box c : children) c.collect(out);
    }
}
