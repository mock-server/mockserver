package org.mockserver.client.serialization.model;

import org.mockserver.model.StringBody;

/**
 * @author jamesdbloom
 */
public class StringBodyDTO extends BodyDTO {

    private String string;

    public StringBodyDTO(StringBody stringBody) {
        this(stringBody, false);
    }

    public StringBodyDTO(StringBody stringBody, Boolean not) {
        super(stringBody.getType(), not);
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
