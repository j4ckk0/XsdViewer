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

/** The WSDL 1.1 names the parser knows: elements of the WSDL namespace and their attributes. */
public final class WsdlVocabulary {

    private WsdlVocabulary() {}

    public static final String NAMESPACE = "http://schemas.xmlsoap.org/wsdl/";

    // elements
    public static final String DEFINITIONS = "definitions";
    public static final String IMPORT = "import";
    public static final String TYPES = "types";
    public static final String MESSAGE = "message";
    public static final String PART = "part";
    public static final String PORT_TYPE = "portType";
    public static final String OPERATION = "operation";
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String FAULT = "fault";
    public static final String BINDING = "binding";
    public static final String SERVICE = "service";
    public static final String PORT = "port";
    public static final String DOCUMENTATION = "documentation";

    // attributes (name, type and targetNamespace are XsdVocabulary's)
    public static final String ATTR_ELEMENT = "element";
    public static final String ATTR_MESSAGE = "message";
    public static final String ATTR_BINDING = "binding";
    public static final String ATTR_LOCATION = "location";
}
