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

import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/**
 * A content model tree as the JSON the page draws from (keys: {@link JsonKey}): every field it reads,
 * the flags only when set, the occurrences only when the box has some. {@link Box} itself knows
 * nothing of JSON, as {@link org.jtools.xsdviewer.schema.SchemaGraph} knows nothing of it.
 */
public final class BoxJsonWriter {

    private BoxJsonWriter() {}

    /** The tree under {@code box}, or the JSON {@code null} when there is none (a side that declares nothing). */
    public static void write(JsonWriter w, Box box) {
        if (box == null) {
            w.nullValue();
            return;
        }
        w.beginObject().property(JsonKey.KIND, box.kind).property(JsonKey.NAME, box.name);
        if (box.id != null) w.property(JsonKey.ID, box.id);
        w.property(JsonKey.PATH, box.path).property(JsonKey.REF, box.ref).property(JsonKey.TYPE_ID, box.typeId)
                .property(JsonKey.TYPE_NAME, box.typeName).property(JsonKey.WORD, box.word).property(JsonKey.NAMESPACE, box.namespace);
        if (box.card != null) {
            w.name(JsonKey.CARD).beginObject().property(JsonKey.MIN, box.card.min()).property(JsonKey.MAX, box.card.max()).endObject();
        }
        if (box.expandable) w.property(JsonKey.EXPANDABLE, true);
        if (box.expanded) w.property(JsonKey.EXPANDED, true);
        if (box.recursive) w.property(JsonKey.RECURSIVE, true);
        if (box.root) w.property(JsonKey.ROOT, true);
        if (box.diff != null) w.property(JsonKey.DIFF, box.diff).property(JsonKey.FOLD_KEY, box.foldKey);
        w.name(JsonKey.ATTRIBUTES).beginArray();
        for (Box a : box.attributes) write(w, a);
        w.endArray().name(JsonKey.CHILDREN).beginArray();
        for (Box c : box.children) write(w, c);
        w.endArray().endObject();
    }
}
