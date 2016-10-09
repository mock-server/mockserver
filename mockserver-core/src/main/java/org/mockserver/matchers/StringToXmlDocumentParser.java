package org.mockserver.matchers;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * @author jamesdbloom
 */
public class StringToXmlDocumentParser extends ObjectWithReflectiveEqualsHashCodeToString {

    public String normaliseXmlString(String matched, ErrorLogger errorLogger) throws IOException, SAXException, ParserConfigurationException {
        return prettyPrintXmlDocument(buildDocument(matched, errorLogger));
    }

    private String prettyPrintXmlDocument(Document doc) throws IOException {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(doc);
            transformer.transform(source, result);
            return result.getWriter().toString();
        } catch (TransformerConfigurationException e) {
            throw new IOException(e);
        } catch (TransformerException e) {
            throw new IOException(e);
        }
    }

    public Document buildDocument(final String matched, final ErrorLogger errorLogger) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        documentBuilder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                errorLogger.logError(matched, exception);
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                errorLogger.logError(matched, exception);
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                errorLogger.logError(matched, exception);
            }
        });
        return documentBuilder.parse(new InputSource(new StringReader(matched)));
    }

    public static interface ErrorLogger {
        public void logError(final String matched, final Exception exception);
    }
}
