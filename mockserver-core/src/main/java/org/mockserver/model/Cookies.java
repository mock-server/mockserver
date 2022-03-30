package org.mockserver.model;

import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class Cookies extends KeysAndValues<Cookie, Cookies> {

    public Cookies(List<Cookie> cookies) {
        withEntries(cookies);
    }

    public Cookies(Cookie... cookies) {
        withEntries(cookies);
    }

    public Cookies(Map<NottableString, NottableString> cookies) {
        super(cookies);
    }

    public static Cookies cookies(Cookie... cookies) {
        return new Cookies(cookies);
    }

    @Override
    public Cookie build(NottableString name, NottableString value) {
        return new Cookie(name, value);
    }

    public Cookies clone() {
        return new Cookies(getMap());
    }
}
