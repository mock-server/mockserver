package org.jamesdbloom.mockserver.client.serialization.model;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.KeyToMultiValue;

/**
 * @author jamesdbloom
 */
public class HeaderDTO extends KeyToMultiValueDTO<String, String> {

    public HeaderDTO(Header header) {
        super(header);
    }
}
