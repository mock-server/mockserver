package org.mockserver.xml;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;

/**
 * @author jamesdbloom
 */
public class StringToXmlDocumentParser extends ObjectWithReflectiveEqualsHashCodeToString {

    public Document buildDocument(final String matched, final ErrorLogger errorLogger) throws ParserConfigurationException, IOException, SAXException {
        return buildDocument(matched, errorLogger, false);
    }

    public Document buildDocument(final String matched, final ErrorLogger errorLogger, boolean namespaceAware) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(namespaceAware);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        documentBuilder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
                errorLogger.logError(matched, exception, ErrorLevel.WARNING);
            }

            @Override
            public void error(SAXParseException exception) {
                errorLogger.logError(matched, exception, ErrorLevel.ERROR);
            }

            @Override
            public void fatalError(SAXParseException exception) {
                errorLogger.logError(matched, exception, ErrorLevel.FATAL_ERROR);
            }
        });
        return documentBuilder.parse(new InputSource(new StringReader(matched)));
    }

    public interface ErrorLogger {
        void logError(final String xmlAsString, final Exception exception, ErrorLevel level);
    }

    public enum ErrorLevel {
        WARNING,
        ERROR,
        FATAL_ERROR;

        public static String prettyPrint(ErrorLevel errorLevel) {
            return errorLevel.name().toLowerCase().replaceAll("_", " ");
        }
    }
}
