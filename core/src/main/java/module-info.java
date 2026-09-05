/*-
 * #%L
 * XsdViewer core
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
 * XsdViewer core: everything the tool knows about a schema, with no dependency but the JDK.
 *
 * <ul>
 * <li>{@code schema} — parsing an XML Schema, a WSDL or a Schematron into a {@code SchemaGraph} of
 *     its declarations and their links, and validating a document against a schema and a Schematron.
 * <li>{@code model} — the content model of one declaration as a tree of boxes, what a document of it
 *     holds, read across the files of a {@code Library}.
 * <li>{@code compare} — two declarations, two texts, two schemas or two workspaces compared.
 * <li>{@code json} — writing those answers as the JSON the page and the HTTP API speak.
 * <li>{@code Messages} — the texts of the messages, in English and French.
 * </ul>
 */
module org.jtools.xsdviewer.core {
    requires java.xml;

    exports org.jtools.xsdviewer;
    exports org.jtools.xsdviewer.schema;
    exports org.jtools.xsdviewer.model;
    exports org.jtools.xsdviewer.compare;
    exports org.jtools.xsdviewer.json;
}
