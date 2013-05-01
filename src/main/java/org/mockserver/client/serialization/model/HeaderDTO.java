package org.mockserver.client.serialization.model;

import org.mockserver.model.Header;

/**
 * @author jamesdbloom
 */
public class HeaderDTO extends KeyToMultiValueDTO<String, String> {

    public HeaderDTO(Header header) {
        super(header);
    }

    protected HeaderDTO() {
    }

    public Header buildObject() {
        return new Header(getName(), getValues());
    }
}
