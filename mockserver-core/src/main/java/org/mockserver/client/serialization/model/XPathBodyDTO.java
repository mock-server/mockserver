package org.mockserver.client.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.XPathBody;

/**
 * @author jamesdbloom
 */
public class XPathBodyDTO extends BodyDTO {

    private String xpath;

    public XPathBodyDTO(XPathBody xPathBody) {
        super(Body.Type.XPATH);
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
