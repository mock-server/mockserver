package org.mockserver.xml;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class XPathEvaluator extends ObjectWithReflectiveEqualsHashCodeToString {

    private final boolean namespaceAware;
    private final XPathExpression xPathExpression;
    private final StringToXmlDocumentParser stringToXmlDocumentParser = new StringToXmlDocumentParser();

    public XPathEvaluator(String expression, Map<String, String> namespacePrefixes) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        if (namespacePrefixes != null) {
            xpath.setNamespaceContext(new NamespaceContext(){
                public String getNamespaceURI(String prefix) {
                    if (namespacePrefixes.containsKey(prefix)) {
                        return namespacePrefixes.get(prefix);
                    }
                    return XMLConstants.NULL_NS_URI;
                }

                // This method isn't necessary for XPath processing.
                public String getPrefix(String uri) {
                    throw new UnsupportedOperationException();
                }

                // This method isn't necessary for XPath processing either.
                public Iterator getPrefixes(String uri) {
                    throw new UnsupportedOperationException();
                }
            });
        }
        namespaceAware = namespacePrefixes != null;
        try {
            xPathExpression = xpath.compile(expression);
        } catch (XPathExpressionException xpee) {
            throw new RuntimeException(xpee.getMessage(), xpee);
        }
    }

    public Object evaluateXPathExpression(String xmlAsString, StringToXmlDocumentParser.ErrorLogger errorLogger, QName returnType) {
        try {
            return xPathExpression.evaluate(stringToXmlDocumentParser.buildDocument(xmlAsString, errorLogger, namespaceAware), returnType);
        } catch (XPathExpressionException | IOException | ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
