package org.mockserver.serialization.model;

import com.google.common.net.MediaType;
import org.mockserver.model.StringBody;

/**
 * @author jamesdbloom
 */
public class StringBodyDTO extends BodyWithContentTypeDTO {

    private String string;
    private boolean subString;

    public StringBodyDTO(StringBody stringBody) {
        this(stringBody, stringBody.getNot());
    }

    public StringBodyDTO(StringBody stringBody, Boolean not) {
        super(stringBody.getType(), not, stringBody.getContentType());
        string = stringBody.getValue();
        subString = stringBody.isSubString();
    }

    public String getString() {
        return string;
    }

    public boolean isSubString() {
        return subString;
    }

    public StringBody buildObject() {
        return new StringBody(string, subString, getMediaType());
    }
}
