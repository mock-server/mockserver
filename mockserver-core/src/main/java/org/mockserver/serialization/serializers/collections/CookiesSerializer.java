package org.mockserver.serialization.serializers.collections;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.Cookie;
import org.mockserver.model.Cookies;

import java.io.IOException;

import static org.mockserver.model.NottableString.serialiseNottableString;

/**
 * @author jamesdbloom
 */
public class CookiesSerializer extends StdSerializer<Cookies> {

    public CookiesSerializer() {
        super(Cookies.class);
    }

    @Override
    public void serialize(Cookies collection, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        for (Cookie cookie : collection.getEntries()) {
            jgen.writeObjectField(serialiseNottableString(cookie.getName()), cookie.getValue());
        }
        jgen.writeEndObject();
    }

}
