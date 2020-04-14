package org.mockserver.serialization.serializers.collections;


import org.mockserver.model.Cookies;

/**
 * @author jamesdbloom
 */
public class CookiesSerializer extends KeysAndValuesSerializer<Cookies> {

    private static final long serialVersionUID = 1L;

    public CookiesSerializer() {
        super(Cookies.class);
    }
   

}
