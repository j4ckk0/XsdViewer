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
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The text of an assertion or diagnostic: its own text, with what a {@code value-of} or a
 * {@code name} stands for filled in by a {@link Leaf} — a placeholder for the graph, the value
 * on the node being checked for the validator — and the text of {@code emph}, {@code span}, {@code dir}.
 */
final class SchematronMessage {

    /** What a {@code value-of select="…"} and a {@code name path="…"} (the path empty for the context node) become. */
    interface Leaf {
        String valueOf(String select);

        String name(String path);
    }

    /** For the graph: {@code {select}} and {@code {name(path)}}. */
    static final Leaf PLACEHOLDERS = new Leaf() {
        @Override
        public String valueOf(String select) { return PLACEHOLDER_OPEN + select + PLACEHOLDER_CLOSE; }

        @Override
        public String name(String path) { return PLACEHOLDER_OPEN + NAME_FUNCTION + path + NAME_FUNCTION_END + PLACEHOLDER_CLOSE; }
    };

    private static final String WHITESPACE = "\\s+";
    private static final char PLACEHOLDER_OPEN = '{', PLACEHOLDER_CLOSE = '}';
    private static final String NAME_FUNCTION = "name(", NAME_FUNCTION_END = ")";

    private SchematronMessage() {}

    static String render(Element e, Leaf leaf) {
        return collect(e, leaf, new StringBuilder()).toString().replaceAll(WHITESPACE, " ").trim();
    }

    private static StringBuilder collect(Element e, Leaf leaf, StringBuilder sb) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(n.getNodeValue());
            } else if (n instanceof Element c && SchematronDom.isSchematron(c)) {
                switch (c.getLocalName()) {
                    case SchematronNames.VALUE_OF -> sb.append(leaf.valueOf(c.getAttribute(SchematronNames.ATTR_SELECT)));
                    case SchematronNames.NAME -> sb.append(leaf.name(c.getAttribute(SchematronNames.ATTR_PATH)));
                    case SchematronNames.EMPH, SchematronNames.SPAN, SchematronNames.DIR -> collect(c, leaf, sb);
                    default -> { }
                }
            }
        }
        return sb;
    }
}
