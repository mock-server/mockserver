package org.mockserver.client.serialization.model;

import org.mockserver.model.Cookie;

/**
 * @author jamesdbloom
 */
public class CookieDTO extends KeyAndValueDTO {

    public CookieDTO(Cookie cookie) {
        this(cookie, false);
    }

    public CookieDTO(Cookie cookie, Boolean not) {
        super(cookie, not);
    }

    protected CookieDTO() {
    }

    public Cookie buildObject() {
        return new Cookie(getName(), getValue());
    }
}
