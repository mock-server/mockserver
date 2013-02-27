package org.jamesdbloom.mockserver.model;

import org.jamesdbloom.mockserver.client.serialization.model.CookieDTO;

/**
 * @author jamesdbloom
 */
public class Cookie extends KeyToMultiValue<String, String> {

    public Cookie(String name, String... value) {
        super(name, value);
    }

    public Cookie(CookieDTO cookie) {
        super(cookie.getName(), cookie.getValues());
    }
}
