package org.jamesdbloom.mockserver.model;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.client.serialization.model.CookieDTO;

/**
 * @author jamesdbloom
 */
public class Cookie extends KeyToMultiValue<String, String> {

    @JsonCreator
    public Cookie(@JsonProperty("name") String name, @JsonProperty("value") String... value) {
        super(name, value);
    }

    public Cookie(CookieDTO cookie) {
        super(cookie.getName(), cookie.getValues());
    }
}
