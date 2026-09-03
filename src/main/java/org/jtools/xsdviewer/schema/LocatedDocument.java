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

import javax.xml.XMLConstants;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A DOM built from text through SAX so that every element remembers where its start tag opens
 * (user data {@link #LINE}, {@link #COLUMN}): a Schematron problem is reported at the line of the
 * node it is about. Namespace declarations are kept as attributes, so the DOM resolves prefixes;
 * comments and processing instructions are not kept, so a rule on {@code comment()} never fires.
 */
final class LocatedDocument {

    static final String LINE = "xsdviewer.line";
    static final String COLUMN = "xsdviewer.column";
    private static final String FEATURE_NAMESPACE_PREFIXES = "http://xml.org/sax/features/namespace-prefixes";
    private static final String XMLNS = "xmlns";

    private LocatedDocument() {}

    /** @throws SAXException when the text is not well-formed XML (the exception says where) */
    static Document parse(String text) throws Exception {
        Document doc = SecureXmlFactories.newDocumentBuilder().newDocument();
        int[] lineStarts = DeclarationLineIndex.lineStarts(text);
        var parser = SecureXmlFactories.newSaxParser();
        parser.getXMLReader().setFeature(FEATURE_NAMESPACE_PREFIXES, true);
        parser.parse(new InputSource(new StringReader(text)), new DefaultHandler() {
            private Locator locator;
            private Node current = doc;

            @Override
            public void setDocumentLocator(Locator l) { locator = l; }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attrs) {
                Element e = doc.createElementNS(uri.isEmpty() ? null : uri, qName);
                for (int i = 0; i < attrs.getLength(); i++) {
                    String q = attrs.getQName(i);
                    boolean ns = q.equals(XMLNS) || q.startsWith(XMLNS + ':');
                    String attrUri = ns ? XMLConstants.XMLNS_ATTRIBUTE_NS_URI : attrs.getURI(i).isEmpty() ? null : attrs.getURI(i);
                    e.setAttributeNS(attrUri, q, attrs.getValue(i));
                }
                if (locator != null) {
                    DeclarationLineIndex.Position p = DeclarationLineIndex.startTag(text, lineStarts, locator);
                    e.setUserData(LINE, p.line(), null);
                    e.setUserData(COLUMN, p.column(), null);
                }
                current.appendChild(e);
                current = e;
            }

            @Override
            public void endElement(String uri, String localName, String qName) { current = current.getParentNode(); }

            @Override
            public void characters(char[] ch, int start, int length) { current.appendChild(doc.createTextNode(new String(ch, start, length))); }
        });
        doc.normalize();
        return doc;
    }

    /** The element a node belongs to: itself, the owner of an attribute, the parent of a text node. */
    static Element elementOf(Node n) {
        if (n instanceof Element e) return e;
        if (n instanceof Attr a) return a.getOwnerElement();
        Node p = n.getParentNode();
        return p instanceof Element e ? e : null;
    }

    static int line(Node n) {
        Element e = elementOf(n);
        return e != null && e.getUserData(LINE) instanceof Integer i ? i : 0;
    }

    static int column(Node n) {
        Element e = elementOf(n);
        return e != null && e.getUserData(COLUMN) instanceof Integer i ? i : 0;
    }
}
