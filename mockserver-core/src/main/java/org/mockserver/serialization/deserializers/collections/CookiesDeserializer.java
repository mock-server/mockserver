package org.mockserver.serialization.deserializers.collections;

import org.mockserver.model.Cookies;

/**
 * @author jamesdbloom
 */
public class CookiesDeserializer extends KeysAndValuesDeserializer<Cookies> {

    private static final long serialVersionUID = 1L;

    public CookiesDeserializer() {
        super(Cookies.class);
    }
    
    @Override
    protected Cookies createObject() {
        return new Cookies();
    }

}
