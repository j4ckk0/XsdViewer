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

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * Turns the text of a schema file into a {@link SchemaGraph}: an XML Schema ({@code xs:schema}) goes
 * to {@link XsdParser}, a WSDL 1.1 ({@code wsdl:definitions}) to {@link WsdlParser}, a Schematron ({@code sch:schema},
 * or a fragment of one) to {@link SchematronParser}; anything else is refused.
 */
public final class SchemaParser {

    private SchemaParser() {}

    /** @throws SchemaException when the text is not XML, or XML that is none of the three */
    public static SchemaGraph parse(String text) throws SchemaException {
        try {
            Document doc = SecureXmlFactories.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
            Element root = doc.getDocumentElement();
            if (XsdVocabulary.NAMESPACE.equals(root.getNamespaceURI()) && XsdVocabulary.SCHEMA.equals(root.getLocalName())) {
                return XsdParser.parse(root, text);
            }
            if (WsdlVocabulary.NAMESPACE.equals(root.getNamespaceURI()) && WsdlVocabulary.DEFINITIONS.equals(root.getLocalName())) {
                return WsdlParser.parse(root, text);
            }
            if (root.getNamespaceURI() != null && SchematronVocabulary.NAMESPACES.contains(root.getNamespaceURI())) {
                return SchematronParser.parse(root, text);
            }
            throw new SchemaException(Messages.get(MessageKey.NOT_A_SCHEMA, root.getTagName()));
        } catch (SchemaException e) {
            throw e;
        } catch (Exception e) {   // the XML parser: not well-formed, or what the JDK refuses (an external entity, say)
            throw new SchemaException(e);
        }
    }
}
