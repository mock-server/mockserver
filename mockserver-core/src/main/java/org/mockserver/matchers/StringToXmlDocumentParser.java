package org.mockserver.matchers;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * @author jamesdbloom
 */
public class StringToXmlDocumentParser extends ObjectWithReflectiveEqualsHashCodeToString {

    String normaliseXmlString(String matched, ErrorLogger errorLogger)
        throws IOException, SAXException, ParserConfigurationException {
        return prettyPrintXmlDocument(buildDocument(matched, errorLogger));
    }

    private static String prettyPrintXmlDocument(Document document) {
        // Pretty-prints a DOM document to XML using DOM Load and Save's LSSerializer.
        // Note that the "format-pretty-print" DOM configuration parameter can only be set in JDK 1.6+.
        DOMImplementation domImplementation = document.getImplementation();
        if (domImplementation.hasFeature("LS", "3.0") && domImplementation.hasFeature("Core", "2.0")) {
            DOMImplementationLS domImplementationLS = (DOMImplementationLS) domImplementation.getFeature("LS", "3.0");
            LSSerializer lsSerializer = domImplementationLS.createLSSerializer();
            DOMConfiguration domConfiguration = lsSerializer.getDomConfig();
            if (domConfiguration.canSetParameter("format-pretty-print", Boolean.TRUE)) {
                lsSerializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
                LSOutput lsOutput = domImplementationLS.createLSOutput();
                lsOutput.setEncoding("UTF-8");
                StringWriter stringWriter = new StringWriter();
                lsOutput.setCharacterStream(stringWriter);
                lsSerializer.write(document, lsOutput);
                return stringWriter.toString();
            } else {
                throw new RuntimeException("DOMConfiguration 'format-pretty-print' parameter isn't settable.");
            }
        } else {
            throw new RuntimeException("DOM 3.0 LS and/or DOM 2.0 Core not supported.");
        }
    }

    public Document buildDocument(final String matched, final ErrorLogger errorLogger) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        documentBuilder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
                errorLogger.logError(matched, exception);
            }

            @Override
            public void error(SAXParseException exception) {
                errorLogger.logError(matched, exception);
            }

            @Override
            public void fatalError(SAXParseException exception) {
                errorLogger.logError(matched, exception);
            }
        });
        return documentBuilder.parse(new InputSource(new StringReader(matched)));
    }

    public interface ErrorLogger {
        void logError(final String matched, final Exception exception);
    }
}
