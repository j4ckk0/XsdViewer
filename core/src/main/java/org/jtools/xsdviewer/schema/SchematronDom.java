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
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

/** Walking a Schematron's DOM: its own elements only (a foreign element, with or without a namespace, is skipped), by name, by id. */
final class SchematronDom {

    private static final String WHITESPACE = "\\s+";

    private SchematronDom() {}

    static boolean isSchematron(Element e) {
        return e.getNamespaceURI() != null && SchematronNames.NAMESPACES.contains(e.getNamespaceURI());
    }

    /** The child elements of {@code e} in a Schematron namespace. */
    static List<Element> children(Element e) {
        return XsdParser.children(e).stream().filter(SchematronDom::isSchematron).toList();
    }

    static List<Element> children(Element e, String localName) {
        return children(e).stream().filter(c -> localName.equals(c.getLocalName())).toList();
    }

    /** The first child named {@code localName}, or null. */
    static Element child(Element e, String localName) {
        List<Element> found = children(e, localName);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Every descendant named {@code localName}, in document order. */
    static List<Element> descendants(Element e, String localName) {
        List<Element> out = new ArrayList<>();
        for (Element c : children(e)) {
            if (localName.equals(c.getLocalName())) out.add(c);
            out.addAll(descendants(c, localName));
        }
        return out;
    }

    /** The descendant of {@code root} named {@code localName} whose {@code id} is {@code id}, or null. */
    static Element byId(Element root, String localName, String id) {
        for (Element e : descendants(root, localName)) if (id.equals(e.getAttribute(SchematronNames.ATTR_ID))) return e;
        return null;
    }

    /** The text of {@code e} with its whitespace collapsed; empty for null. */
    static String text(Element e) {
        return e == null ? "" : e.getTextContent().replaceAll(WHITESPACE, " ").trim();
    }
}
