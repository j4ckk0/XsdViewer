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

import java.util.Set;

/**
 * The kinds of node of a {@link SchemaGraph}. A global declaration has the kind of the XSD
 * (or WSDL) element that declares it; two more kinds stand for what the file does not declare.
 */
public final class NodeKind {

    private NodeKind() {}

    public static final String ELEMENT = XsdVocabulary.ELEMENT;
    public static final String COMPLEX_TYPE = XsdVocabulary.COMPLEX_TYPE;
    public static final String SIMPLE_TYPE = XsdVocabulary.SIMPLE_TYPE;
    public static final String GROUP = XsdVocabulary.GROUP;
    public static final String ATTRIBUTE_GROUP = XsdVocabulary.ATTRIBUTE_GROUP;
    public static final String ATTRIBUTE = XsdVocabulary.ATTRIBUTE;
    /** A built-in XSD type (xs:string, xs:int...). */
    public static final String BUILTIN = "builtin";
    /** An object referenced but not declared in this file (imported / included). */
    public static final String EXTERNAL = "external";

    // the declarations of a WSDL 1.1 file (an operation is declared inside its portType)
    public static final String SERVICE = WsdlVocabulary.SERVICE;
    public static final String PORT_TYPE = WsdlVocabulary.PORT_TYPE;
    public static final String OPERATION = WsdlVocabulary.OPERATION;
    public static final String BINDING = WsdlVocabulary.BINDING;
    public static final String MESSAGE = WsdlVocabulary.MESSAGE;

    /** Kind of a type reference ({@code type:X}) not yet known to be a complexType or a simpleType; kept for an external type. */
    public static final String TYPE_REFERENCE = "type";

    /** The kinds of global declaration (the XSD elements allowed under xs:schema that have a name). */
    public static final Set<String> GLOBAL_DECLARATIONS =
            Set.of(ELEMENT, COMPLEX_TYPE, SIMPLE_TYPE, GROUP, ATTRIBUTE_GROUP, ATTRIBUTE);

    /** The kinds of named declaration right under wsdl:definitions. */
    public static final Set<String> WSDL_DECLARATIONS = Set.of(MESSAGE, PORT_TYPE, BINDING, SERVICE);
}
