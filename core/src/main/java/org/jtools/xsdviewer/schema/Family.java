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
 * The families a declaration may belong to: the service description of a WSDL, the rules of a
 * Schematron. An XML Schema object — which a WSDL's inline schemas and a Schematron's targets also
 * are — belongs to none; the page draws a family's objects and the links to them apart from the
 * schema's own.
 */
public final class Family {

    public static final String WSDL = "wsdl";
    public static final String SCHEMATRON = "schematron";

    private Family() {}
}
