package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class Cookies extends KeysAndValues<Cookie, Cookies> {

    @Override
    public Cookie build(NottableString name, NottableString value) {
        return new Cookie(name, value);
    }

    public Cookies clone() {
        return new Cookies().withEntries(getEntries());
    }
}
