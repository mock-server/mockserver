package org.mockserver.templates.engine.helpers;

import org.mockserver.xml.StringToXmlDocumentParser;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * XPath evaluation helper for templates: evaluate an XPath expression against an
 * XML document and return the string result. Uses the JDK {@code javax.xml.xpath}
 * APIs (these stay in the {@code javax} namespace per the Java compatibility
 * policy — they are JDK classes, not the jakarta-migrated EE ones).
 * <p>
 * The XML parser is hardened against XXE by disabling DOCTYPE declarations and
 * external entity resolution.
 */
public class XmlXPathTemplateHelper {

    /**
     * Evaluates the given XPath expression against the supplied XML string and
     * returns the result as a string. Returns an empty string for {@code null}
     * inputs or when the document cannot be parsed / the expression is invalid.
     */
    public String evaluate(String xml, String xpathExpression) {
        if (xml == null || xpathExpression == null) {
            return "";
        }
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            // harden against XXE
            documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            documentBuilderFactory.setXIncludeAware(false);
            documentBuilderFactory.setExpandEntityReferences(false);
            documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            documentBuilderFactory.setNamespaceAware(true);

            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            XPathFactory xPathFactory = XPathFactory.newInstance();
            xPathFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            XPath xPath = xPathFactory.newXPath();
            String result = (String) xPath.evaluate(xpathExpression, document, XPathConstants.STRING);
            return result != null ? result : "";
        } catch (Exception exception) {
            return "";
        }
    }

    static final int MAX_FORMAT_DEPTH = 256;
    private static final int MIN_FORMAT_OUTPUT_CHARS = 64 * 1024;

    /**
     * The XML re-indented with two spaces per level, keeping its declaration, comments and CDATA. The parser
     * refuses a DOCTYPE and external entities. Text that is not well-formed XML, XML nested deeper than
     * {@value #MAX_FORMAT_DEPTH} elements, or output over four times the input (at least 64 KiB) makes the
     * template fail.
     */
    public String formatXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("xpath.formatXml: there is no XML to format");
        }
        Document document;
        try {
            document = new StringToXmlDocumentParser().buildDocument(xml, (text, exception, level) -> {
            }, true);
        } catch (Exception notXml) {
            String hint = xml.contains("&lt;") ? "; in Mustache read the value with triple braces, such as {{{ request.body }}}, so it is not HTML-escaped" : "";
            throw new IllegalArgumentException("xpath.formatXml: the text is not well-formed XML (" + notXml.getMessage() + ")" + hint, notXml);
        }
        removeIndentation(document);
        int limit = Math.max(MIN_FORMAT_OUTPUT_CHARS, xml.length() * 4);
        StringWriter writer = new StringWriter() {
            @Override
            public void write(String text, int offset, int length) {
                checkLimit(length);
                super.write(text, offset, length);
            }

            @Override
            public void write(char[] text, int offset, int length) {
                checkLimit(length);
                super.write(text, offset, length);
            }

            @Override
            public void write(int character) {
                checkLimit(1);
                super.write(character);
            }

            private void checkLimit(int length) {
                if (getBuffer().length() + length > limit) {
                    throw new IllegalArgumentException("xpath.formatXml: the formatted XML would be over " + limit + " characters");
                }
            }
        };
        try {
            Transformer transformer;
            synchronized (XmlFormatting.TRANSFORMER_FACTORY) {
                transformer = XmlFormatting.TRANSFORMER_FACTORY.newTransformer();
            }
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(document), new StreamResult(writer));
        } catch (IllegalArgumentException tooLarge) {
            throw tooLarge;
        } catch (Exception formattingFailure) {
            Throwable cause = formattingFailure.getCause();
            if (cause instanceof IllegalArgumentException tooLarge) {
                throw tooLarge;
            }
            throw new IllegalArgumentException("xpath.formatXml: the XML could not be formatted (" + formattingFailure.getMessage() + ")", formattingFailure);
        }
        String trimmed = xml.stripLeading();
        String declaration = "";
        if (trimmed.startsWith("<?xml")) {
            int end = trimmed.indexOf("?>");
            declaration = trimmed.substring(0, end + 2) + "\n";
        }
        return declaration + writer.toString().strip();
    }

    /**
     * Removes the whitespace-only text between elements, which the transformer would otherwise keep beside
     * its own indentation, and refuses XML nested too deeply to format safely.
     */
    private static void removeIndentation(Document document) {
        Deque<Node> elements = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        elements.push(document.getDocumentElement());
        depths.push(1);
        while (!elements.isEmpty()) {
            Node element = elements.pop();
            int depth = depths.pop();
            if (depth > MAX_FORMAT_DEPTH) {
                throw new IllegalArgumentException("xpath.formatXml: the XML is nested more than " + MAX_FORMAT_DEPTH + " elements deep");
            }
            boolean hasElementChildren = false;
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    hasElementChildren = true;
                    elements.push(child);
                    depths.push(depth + 1);
                }
            }
            if (hasElementChildren) {
                Node child = element.getFirstChild();
                while (child != null) {
                    Node next = child.getNextSibling();
                    if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                        element.removeChild(child);
                    }
                    child = next;
                }
            }
        }
    }

    // loaded only when a template formats XML; TransformerFactory is not thread-safe, so its use is synchronized
    private static final class XmlFormatting {
        private static final TransformerFactory TRANSFORMER_FACTORY = newTransformerFactory();

        private static TransformerFactory newTransformerFactory() {
            TransformerFactory factory = TransformerFactory.newInstance();
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            } catch (Exception unsupported) {
                // the JDK transformer supports these; an identity transform reads no external resource either way
            }
            return factory;
        }
    }

    @Override
    public String toString() {
        return "XmlXPathTemplateHelper";
    }
}
