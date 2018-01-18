package org.mockserver.client.serialization.model;

import org.mockserver.model.Cookie;

/**
 * @author jamesdbloom
 */
public class CookieDTO extends KeyAndValueDTO implements DTO<Cookie> {

    public CookieDTO(Cookie cookie) {
        super(cookie);
    }

    protected CookieDTO() {
    }

    public Cookie buildObject() {
        return new Cookie(getName(), getValue());
    }
}
