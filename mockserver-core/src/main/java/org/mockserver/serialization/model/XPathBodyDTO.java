package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.XPathBody;

import java.util.Map;

/**
 * @author jamesdbloom
 */
public class XPathBodyDTO extends BodyDTO {

    private final String xpath;
    private final Map<String, String> namespacePrefixes;

    public XPathBodyDTO(XPathBody xPathBody) {
        this(xPathBody, null);
    }

    public XPathBodyDTO(XPathBody xPathBody, Boolean not) {
        super(Body.Type.XPATH, not);
        this.xpath = xPathBody.getValue();
        this.namespacePrefixes = xPathBody.getNamespacePrefixes();
        withOptional(xPathBody.getOptional());
    }

    public String getXPath() {
      return xpath;
    }

    public Map<String, String> getNamespacePrefixes() {
        return namespacePrefixes;
    }

    public XPathBody buildObject() {
        return (XPathBody) new XPathBody(getXPath(), getNamespacePrefixes()).withOptional(getOptional());
    }
}
