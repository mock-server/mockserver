package org.mockserver.client.serialization.model;

import org.mockserver.model.Header;

/**
 * @author jamesdbloom
 */
public class HeaderDTO extends KeyToMultiValueDTO {

    public HeaderDTO(Header header) {
        super(header, false);
    }

    public HeaderDTO(Header header, Boolean not) {
        super(header, not);
    }

    protected HeaderDTO() {
    }

    public Header buildObject() {
        return new Header(getName(), getValues());
    }
}
