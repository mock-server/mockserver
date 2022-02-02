package org.mockserver.serialization.model;

import org.mockserver.model.Cookie;
import org.mockserver.model.Cookies;
import org.mockserver.model.CookiesModifier;

public class CookiesModifierDTO extends KeysAndValuesModifierDTO<Cookies, CookiesModifier, Cookie, CookiesModifierDTO> {

    public CookiesModifierDTO() {
    }

    public CookiesModifierDTO(CookiesModifier headersModifier) {
        super(headersModifier);
    }

    @Override
    CookiesModifier newKeysAndValuesModifier() {
        return new CookiesModifier();
    }
}
