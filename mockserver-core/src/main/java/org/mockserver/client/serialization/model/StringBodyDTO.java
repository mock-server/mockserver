package org.mockserver.client.serialization.model;

import org.mockserver.model.StringBody;

/**
 * @author jamesdbloom
 */
public class StringBodyDTO extends BodyDTO {

    private String string;

    public StringBodyDTO(StringBody stringBody) {
        super(stringBody.getType());
        string = stringBody.getValue();
    }

    protected StringBodyDTO() {
    }

    public String getString() {
        return string;
    }

    public StringBody buildObject() {
        return new StringBody(string);
    }
}
