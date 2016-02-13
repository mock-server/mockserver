package org.mockserver.matchers;

import com.google.common.base.Charsets;
import org.apache.xml.serialize.Method;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;

/**
 * @author jamesdbloom
 */
public class StringToXmlDocumentParser extends ObjectWithReflectiveEqualsHashCodeToString {

    public String normaliseXmlString(String matched, ErrorLogger errorLogger) throws IOException, SAXException, ParserConfigurationException {
        return prettyPrintXmlDocument(buildDocument(matched, errorLogger));
    }

    private String prettyPrintXmlDocument(Document doc) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        new XMLSerializer(byteArrayOutputStream, new OutputFormat(Method.XML, Charsets.UTF_8.name(), true)).serialize(doc);
        return byteArrayOutputStream.toString(Charsets.UTF_8.name());
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
