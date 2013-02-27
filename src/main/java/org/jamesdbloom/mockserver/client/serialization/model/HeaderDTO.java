package org.jamesdbloom.mockserver.client.serialization.model;

import org.jamesdbloom.mockserver.model.Header;

/**
 * @author jamesdbloom
 */
public class HeaderDTO extends KeyToMultiValueDTO<String, String> {

    public HeaderDTO(Header header) {
        super(header);
    }

    public HeaderDTO() {
    }
}
