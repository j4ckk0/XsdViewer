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
 * XsdViewer core: parses an XML Schema, a WSDL or a Schematron into a graph of its declarations,
 * their links and their content models ({@code schema}), writes that graph as JSON ({@code json}),
 * validates a document against a schema and a Schematron, and carries the texts of its messages in
 * English and French ({@code Messages}). The JDK and nothing else.
 */
module org.jtools.xsdviewer.core {
    requires java.xml;

    exports org.jtools.xsdviewer;
    exports org.jtools.xsdviewer.schema;
    exports org.jtools.xsdviewer.json;
}
