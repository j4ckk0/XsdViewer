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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/**
 * The line where each declaration's start tag opens, by node id. Which tags declare a node is
 * the parser's business ({@link DeclarationId}): the walk hands it the path of open tags. The SAX
 * locator points after the start tag, so the '<' is looked for backwards: a tag spread over several lines gets its first.
 */
final class DeclarationLineIndex {

    /** An open element: its namespace, local name and {@code name} attribute (null when it has none). */
    record Tag(String uri, String localName, String name) {
        boolean is(String namespace, String local) {
            return namespace.equals(uri) && local.equals(localName);
        }
    }

    /** The id of the node declared by the last tag of {@code path} (the root first), or null when it declares none. */
    @FunctionalInterface
    interface DeclarationId {
        String of(List<Tag> path);
    }

    /** The lines a declaration spans: where its start tag opens and where its end tag closes (the same line when it is self-closed). */
    record Span(int start, int end) {}

    private DeclarationLineIndex() {}

    static Map<String, Span> build(String text, DeclarationId idOf) throws Exception {
        int[] lineStarts = lineStarts(text);
        Map<String, Span> result = new HashMap<>();

        SecureXmlFactories.newSaxParser().parse(new InputSource(new StringReader(text)), new DefaultHandler() {
            private Locator locator;
            private final List<Tag> path = new ArrayList<>();
            /** The declaration each depth opened, so the matching end tag closes the right one; null where a tag declares nothing. */
            private final List<String> open = new ArrayList<>();

            @Override
            public void setDocumentLocator(Locator l) { locator = l; }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attrs) {
                path.add(new Tag(uri, localName, attrs.getValue(XsdVocabulary.ATTR_NAME)));
                String id = idOf.of(path);
                boolean first = id != null && locator != null && !result.containsKey(id);
                if (first) result.put(id, new Span(startTagLine(text, lineStarts, locator), 0));
                open.add(first ? id : null);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                path.remove(path.size() - 1);
                String id = open.remove(open.size() - 1);
                // the locator points just past the '>' of the end tag, which is where the declaration stops
                if (id != null && locator != null) result.put(id, new Span(result.get(id).start(), locator.getLineNumber()));
            }
        });
        return result;
    }

    /**
     * The lines every element spans, in document order (the root first): for a vocabulary whose
     * declarations carry no name, the parser finds a node's lines by the rank of its element, the
     * same in its DOM walk as here.
     */
    static List<Span> elementSpans(String text) throws Exception {
        int[] lineStarts = lineStarts(text);
        List<Span> result = new ArrayList<>();
        SecureXmlFactories.newSaxParser().parse(new InputSource(new StringReader(text)), new DefaultHandler() {
            private Locator locator;
            /** The rank of each element still open, its end tag not read yet. */
            private final List<Integer> open = new ArrayList<>();

            @Override
            public void setDocumentLocator(Locator l) { locator = l; }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attrs) {
                open.add(result.size());
                result.add(new Span(locator == null ? 0 : startTagLine(text, lineStarts, locator), 0));
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                int rank = open.remove(open.size() - 1);
                if (locator != null) result.set(rank, new Span(result.get(rank).start(), locator.getLineNumber()));
            }
        });
        return result;
    }

    /** A place in the text, 1-based. */
    record Position(int line, int column) {}

    private static int startTagLine(String text, int[] lineStarts, Locator locator) {
        return startTag(text, lineStarts, locator).line();
    }

    /** Where the '<' of the start tag the locator points after is (a tag spread over several lines gets its first line). */
    static Position startTag(String text, int[] lineStarts, Locator locator) {
        int line = locator.getLineNumber();
        int col = locator.getColumnNumber();
        if (line > 0 && line <= lineStarts.length) {
            int offset = Math.min(text.length(), lineStarts[line - 1] + Math.max(0, col - 1));
            int lt = text.lastIndexOf('<', Math.max(0, offset - 1));
            if (lt >= 0) {
                line = lineOf(lineStarts, lt);
                col = lt - lineStarts[line - 1] + 1;
            }
        }
        return new Position(line, col);
    }

    /** Offset of the first character of each line. */
    static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') starts.add(i + 1);
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 1-based line containing the character at {@code offset}. */
    private static int lineOf(int[] lineStarts, int offset) {
        int i = Arrays.binarySearch(lineStarts, offset);
        if (i < 0) i = -i - 2;
        return i + 1;
    }
}
