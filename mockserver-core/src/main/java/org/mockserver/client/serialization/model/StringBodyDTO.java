package org.mockserver.client.serialization.model;

import org.mockserver.model.StringBody;

/**
 * @author jamesdbloom
 */
public class StringBodyDTO extends BodyDTO {

    private String value;

    public StringBodyDTO(StringBody stringBody) {
        super(stringBody.getType());
        value = stringBody.getValue();
    }

    protected StringBodyDTO() {
    }

    public String getValue() {
        return value;
    }

    public StringBody buildObject() {
        return new StringBody(value, getType());
    }
}
