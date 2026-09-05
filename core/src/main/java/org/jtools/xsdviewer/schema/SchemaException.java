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
 * A text that cannot be read as a schema: not XML, XML that is neither an XML Schema, a WSDL nor a
 * Schematron, a Schematron that names an unknown phase, an XML Schema the JDK cannot compile. The
 * message says why, in the language of the request being served ({@link org.jtools.xsdviewer.Messages}),
 * and the cause, when there is one, is what the XML parser or the XPath engine threw.
 */
public final class SchemaException extends Exception {

    private static final long serialVersionUID = 1L;

    public SchemaException(String message) {
        super(message);
    }

    /** Wraps what a parser threw, keeping its message — or its name, when it carries none. */
    public SchemaException(Throwable cause) {
        super(cause.getMessage() == null ? cause.toString() : cause.getMessage(), cause);
    }
}
