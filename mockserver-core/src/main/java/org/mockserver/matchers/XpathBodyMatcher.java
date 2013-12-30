package org.mockserver.matchers;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.ModelObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

public class XpathBodyMatcher extends ModelObject implements Matcher<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(XpathBodyMatcher.class);

    private final Map<String, String> xpathToValueMap;

    private final XPath xpathEngine;

    private final DocumentBuilder documentBuilder;

    public XpathBodyMatcher(Map<String, String> xpathToValueMap, XPath xpathEngine, DocumentBuilder documentBuilder) {
        this.xpathToValueMap = xpathToValueMap;
        this.xpathEngine = xpathEngine;
        this.documentBuilder = documentBuilder;
    }

    @Override
    public boolean matches(String body) {
        if (empty()) {
            return true;
        } else {
            Document document = null;
            try {
                document = documentBuilder.parse(new ByteArrayInputStream(body.getBytes("UTF-8")));
            } catch (SAXException e) {
                LOGGER.trace(e.getMessage(), e);
                return false;
            } catch (IOException e) {
                LOGGER.trace(e.getMessage(), e);
                return false;
            }
            for (Map.Entry<String, String> entry : xpathToValueMap.entrySet()) {
                String result;
                try {
                    result = xpathEngine.evaluate(entry.getKey(), document);
                } catch (XPathExpressionException e) {
                    LOGGER.trace(e.getMessage(), e);
                    return false;
                }
                if (!StringUtils.equals(result, entry.getValue())) {
                    LOGGER.trace("Failed comparison: [{} vs {}] for xpath: [{}]", new Object[] {entry.getValue(), result, entry.getKey()});
                    return false;
                }
            }
            return true;
        }
    }

    private boolean empty() {
        return xpathToValueMap == null || xpathToValueMap.isEmpty();
    }
}
