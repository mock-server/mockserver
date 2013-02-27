package org.jamesdbloom.mockserver.model;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.client.serialization.model.HeaderDTO;

/**
 * @author jamesdbloom
 */
public class Header extends KeyToMultiValue<String, String> {

    @JsonCreator
    public Header(@JsonProperty("name") String name, @JsonProperty("value") String... value) {
        super(name, value);
    }

    public Header(HeaderDTO header) {
        super(header.getName(), header.getValues());
    }
}
