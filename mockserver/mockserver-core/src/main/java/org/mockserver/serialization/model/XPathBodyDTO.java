package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.model.XPathBody;

import java.util.Map;

/**
 * @author jamesdbloom
 */
public class XPathBodyDTO extends BodyDTO {

    private final String xpath;
    private final Map<String, String> namespacePrefixes;
    private final NottableDateTimeString dateTime;

    public XPathBodyDTO(XPathBody xPathBody) {
        this(xPathBody, null);
    }

    public XPathBodyDTO(XPathBody xPathBody, Boolean not) {
        super(Body.Type.XPATH, not);
        this.xpath = xPathBody.getValue();
        this.namespacePrefixes = xPathBody.getNamespacePrefixes();
        this.dateTime = xPathBody.getDateTime();
        withOptional(xPathBody.getOptional());
    }

    public String getXPath() {
      return xpath;
    }

    public Map<String, String> getNamespacePrefixes() {
        return namespacePrefixes;
    }

    public NottableDateTimeString getDateTime() {
        return dateTime;
    }

    public XPathBody buildObject() {
        return (XPathBody) new XPathBody(getXPath(), getNamespacePrefixes(), getDateTime()).withOptional(getOptional());
    }
}
