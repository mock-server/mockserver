package org.mockserver.client.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.XPathBody;

/**
 * @author jamesdbloom
 */
public class XPathBodyDTO extends BodyDTO {

    private String xpath;

    public XPathBodyDTO(XPathBody xPathBody) {
        this(xPathBody, false);
    }

    public XPathBodyDTO(XPathBody xPathBody, boolean not) {
        super(Body.Type.XPATH, not);
        this.xpath = xPathBody.getValue();
    }

    protected XPathBodyDTO() {
    }

    public String getXPath() {
        return xpath;
    }

    public XPathBody buildObject() {
        return new XPathBody(getXPath());
    }
}
