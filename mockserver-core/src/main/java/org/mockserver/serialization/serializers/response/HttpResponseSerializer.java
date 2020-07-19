package org.mockserver.serialization.serializers.response;

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
        if (httpResponse.getStatusCode() != null) {
            jgen.writeObjectField("statusCode", httpResponse.getStatusCode());
        }
        if (httpResponse.getReasonPhrase() != null) {
            jgen.writeObjectField("reasonPhrase", httpResponse.getReasonPhrase());
        }
        if (httpResponse.getHeaderList() != null && !httpResponse.getHeaderList().isEmpty()) {
            jgen.writeObjectField("headers", httpResponse.getHeaders());
        }
        if (httpResponse.getCookieList() != null && !httpResponse.getCookieList().isEmpty()) {
            jgen.writeObjectField("cookies", httpResponse.getCookies());
        }
        Body<?> body = httpResponse.getBody();
        if (body != null) {
            if (body instanceof StringBody && !((StringBody) body).getValue().isEmpty()) {
                jgen.writeObjectField("body", body);
            } else if (body instanceof JsonBody && !((JsonBody) body).getValue().isEmpty()) {
                jgen.writeObjectField("body", body);
            } else if (body instanceof BinaryBody && ((BinaryBody) body).getValue().length > 0) {
                jgen.writeObjectField("body", body);
            } else if (body instanceof ParameterBody && !((ParameterBody) body).getValue().isEmpty()) {
                jgen.writeObjectField("body", body);
            } else if (body instanceof XmlBody && !((XmlBody) body).getValue().isEmpty()) {
                jgen.writeObjectField("body", body);
            } else if (body instanceof LogEntryBody) {
                jgen.writeObjectField("body", body);
            }
        }
        if (httpResponse.getDelay() != null) {
            jgen.writeObjectField("delay", httpResponse.getDelay());
        }
        if (httpResponse.getConnectionOptions() != null) {
            jgen.writeObjectField("connectionOptions", httpResponse.getConnectionOptions());
        }
        jgen.writeEndObject();
    }
}
