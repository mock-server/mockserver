package org.mockserver.model;

import java.util.List;

public class CookiesModifier extends KeysAndValuesModifier<Cookies, CookiesModifier, Cookie> {

    /**
     * Static builder to create a cookies modifier.
     */
    public static CookiesModifier cookiesModifier() {
        return new CookiesModifier();
    }

    @Override
    Cookies construct(List<Cookie> cookies) {
        return new Cookies(cookies);
    }

    @Override
    Cookies construct(Cookie... cookies) {
        return new Cookies(cookies);
    }

}
