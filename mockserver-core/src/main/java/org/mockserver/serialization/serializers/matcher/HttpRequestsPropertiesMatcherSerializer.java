package org.mockserver.serialization.serializers.matcher;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.matchers.HttpRequestPropertiesMatcher;
import org.mockserver.matchers.HttpRequestsPropertiesMatcher;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class HttpRequestsPropertiesMatcherSerializer extends StdSerializer<HttpRequestsPropertiesMatcher> {

    public HttpRequestsPropertiesMatcherSerializer() {
        super(HttpRequestsPropertiesMatcher.class);
    }

    @Override
    public void serialize(HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartArray();
        if (httpRequestsPropertiesMatcher.getHttpRequestPropertiesMatchers() != null) {
            for (HttpRequestPropertiesMatcher httpRequestPropertiesMatcher : httpRequestsPropertiesMatcher.getHttpRequestPropertiesMatchers()) {
                jgen.writeObject(httpRequestPropertiesMatcher);
            }
        }
        jgen.writeEndArray();
    }

}
