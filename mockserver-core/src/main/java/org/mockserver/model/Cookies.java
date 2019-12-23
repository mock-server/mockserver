package org.mockserver.model;

import java.util.List;

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

    @Override
    public Cookie build(NottableString name, NottableString value) {
        return new Cookie(name, value);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Cookies clone() {
        return new Cookies().withEntries(getEntries());
    }
}
