package org.mockserver.serialization.model;

import org.mockserver.model.StringBody;

/**
 * @author jamesdbloom
 */
public class StringBodyDTO extends BodyWithContentTypeDTO {

    private final String string;
    private final boolean subString;
    private final byte[] rawBytes;

    public StringBodyDTO(StringBody stringBody) {
        this(stringBody, stringBody.getNot());
    }

    public StringBodyDTO(StringBody stringBody, Boolean not) {
        super(stringBody.getType(), not, stringBody);
        string = stringBody.getValue();
        subString = stringBody.isSubString();
        rawBytes = stringBody.getRawBytes();
    }

    public String getString() {
        return string;
    }

    public boolean isSubString() {
        return subString;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public StringBody buildObject() {
        return new StringBody(getString(), getRawBytes(), isSubString(), getMediaType());
    }
}
