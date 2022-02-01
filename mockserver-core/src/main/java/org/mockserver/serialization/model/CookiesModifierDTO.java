package org.mockserver.serialization.model;

import org.mockserver.model.Cookies;
import org.mockserver.model.CookiesModifier;

public class CookiesModifierDTO extends KeysAndValuesModifierDTO<Cookies, CookiesModifier, CookiesModifierDTO> {

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
