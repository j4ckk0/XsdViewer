package org.jtools.xsdviewer.schema;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

/**
 * Namespace-aware XML parsers that never touch the network or the file system: no external DTD,
 * no external entities. The schema text is the only thing read.
 */
final class SecureXmlFactories {

    private static final String FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

    private SecureXmlFactories() {}

    static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setExpandEntityReferences(false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
        f.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        f.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        return f.newDocumentBuilder();
    }

    static SAXParser newSaxParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
        f.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        f.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        return f.newSAXParser();
    }
}
