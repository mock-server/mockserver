package org.mockserver.serialization.serializers.matcher;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.matchers.HttpRequestPropertiesMatcher;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class HttpRequestPropertiesMatcherSerializer extends StdSerializer<HttpRequestPropertiesMatcher> {

    public HttpRequestPropertiesMatcherSerializer() {
        super(HttpRequestPropertiesMatcher.class);
    }

    @Override
    public void serialize(HttpRequestPropertiesMatcher requestPropertiesMatcher, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        if (requestPropertiesMatcher.getHttpRequest() != null) {
            jgen.writeObject(requestPropertiesMatcher.getHttpRequest());
        }
    }

}
