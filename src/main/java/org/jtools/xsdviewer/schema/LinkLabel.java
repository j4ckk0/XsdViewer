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

/**
 * Labels of the edges of a {@link SchemaGraph}: the nature of a reference. Model vocabulary handed
 * to the page, not user-interface text (a nested element's type link is labelled with the element's name,
 * a WSDL service's link to a portType with the port's name, a message's link to an element with the part's name).
 */
public final class LinkLabel {

    private LinkLabel() {}

    /** A global element / attribute to its declared type. */
    public static final String TYPE = "type";
    /** {@code <xs:element ref="..."/>} inside a declaration. */
    public static final String REF = "ref";
    /** {@code <xs:attribute ref="..."/>} inside a declaration. */
    public static final String ATTRIBUTE_REF = "attribute ref";
    /** A global element to the head of its substitution group. */
    public static final String SUBSTITUTES = "substitutes";
    /** {@code <xs:group ref="..."/>} */
    public static final String GROUP = "group";
    /** {@code <xs:attributeGroup ref="..."/>} */
    public static final String ATTRIBUTE_GROUP = "attributeGroup";
    public static final String EXTENDS = "extends";
    public static final String RESTRICTS = "restricts";
    public static final String LIST_OF = "list of";
    public static final String UNION_OF = "union of";
    /** Prefix of the label of a {@code xs:keyref}'s link to the element declaring the key it refers to: {@code "keyref name"}. */
    public static final String KEYREF_PREFIX = "keyref ";

    /** The label of a keyref named {@code name}. */
    public static String keyref(String name) {
        return KEYREF_PREFIX + name;
    }

    /** A WSDL portType to one of its operations. */
    public static final String OPERATION = "operation";
    /** A WSDL operation to its input / output / fault message. */
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String FAULT = "fault";
    /** A WSDL binding to the portType it binds. */
    public static final String BINDS = "binds";

    private static final String ATTRIBUTE_PREFIX = "attribute ";

    /** A nested attribute {@code name} to its type: {@code "attribute name"}. */
    public static String attribute(String name) {
        return ATTRIBUTE_PREFIX + name;
    }
}
