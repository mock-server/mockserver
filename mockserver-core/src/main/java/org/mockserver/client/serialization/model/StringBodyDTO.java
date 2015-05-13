package org.mockserver.client.serialization.model;

import org.mockserver.model.StringBody;

import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public class StringBodyDTO extends BodyDTO {

    private String string;
    private Charset charset;

    public StringBodyDTO(StringBody stringBody) {
        this(stringBody, false);
    }

    public StringBodyDTO(StringBody stringBody, Boolean not) {
        super(stringBody.getType(), not);
        string = stringBody.getValue();
        charset = stringBody.getCharset();
    }

    protected StringBodyDTO() {
    }

    public String getString() {
        return string;
    }

    public Charset getCharset() {
        return charset;
    }

    public StringBody buildObject() {
        return new StringBody(string, charset);
    }
}
