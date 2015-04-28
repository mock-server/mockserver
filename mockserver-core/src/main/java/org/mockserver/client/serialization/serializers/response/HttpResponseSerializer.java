package org.mockserver.client.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.*;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class HttpResponseSerializer extends StdSerializer<HttpResponse> {

    public HttpResponseSerializer() {
        super(HttpResponse.class);
    }

    @Override
    public void serialize(HttpResponse httpResponse, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (httpResponse.getStatusCode() != null && httpResponse.getStatusCode() != 200) {
            jgen.writeObjectField("statusCode", httpResponse.getStatusCode());
        }
        if (httpResponse.getHeaders() != null && !httpResponse.getHeaders().isEmpty()) {
            jgen.writeObjectField("headers", httpResponse.getHeaders());
        }
        if (httpResponse.getCookies() != null && !httpResponse.getCookies().isEmpty()) {
            jgen.writeObjectField("cookies", httpResponse.getCookies());
        }
        Body body = httpResponse.getBody();
        if (body != null) {
            if (body instanceof StringBody && !((StringBody) body).getValue().isEmpty()) {
                jgen.writeObjectField("body", ((StringBody) body).getValue());
            } else if (body instanceof JsonBody && !((JsonBody) body).getValue().isEmpty()) {
                jgen.writeObjectField("body", ((JsonBody) body).getValue());
            } else if (body instanceof BinaryBody) {
                jgen.writeObjectField("body", body);
            }
        }
        if (httpResponse.getDelay() != null) {
            jgen.writeObjectField("delay", httpResponse.getDelay());
        }
        jgen.writeEndObject();
    }
}
