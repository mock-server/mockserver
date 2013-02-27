package org.jamesdbloom.mockserver.client.serialization.model;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.KeyToMultiValue;

/**
 * @author jamesdbloom
 */
public class CookieDTO extends KeyToMultiValueDTO<String, String> {

    public CookieDTO(Cookie cookie) {
        super(cookie);
    }
}
