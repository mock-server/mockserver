package org.mockserver.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.HttpRequestDTO;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class HttpRequestDTOSerializer extends StdSerializer<HttpRequestDTO> {

    public HttpRequestDTOSerializer() {
        super(HttpRequestDTO.class);
    }

    @Override
    public void serialize(HttpRequestDTO httpRequest, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (httpRequest.getNot() != null && httpRequest.getNot()) {
            jgen.writeBooleanField("not", httpRequest.getNot());
        }
        if (httpRequest.getMethod() != null && isNotBlank(httpRequest.getMethod().getValue())) {
            jgen.writeObjectField("method", httpRequest.getMethod());
        }
        if (httpRequest.getPath() != null && isNotBlank(httpRequest.getPath().getValue())) {
            jgen.writeObjectField("path", httpRequest.getPath());
        }
        if (httpRequest.getQueryStringParameters() != null && !httpRequest.getQueryStringParameters().isEmpty()) {
            jgen.writeObjectField("queryStringParameters", httpRequest.getQueryStringParameters());
        }
        if (httpRequest.getHeaders() != null && !httpRequest.getHeaders().isEmpty()) {
            jgen.writeObjectField("headers", httpRequest.getHeaders());
        }
        if (httpRequest.getCookies() != null && !httpRequest.getCookies().isEmpty()) {
            jgen.writeObjectField("cookies", httpRequest.getCookies());
        }
        if (httpRequest.getKeepAlive() != null) {
            jgen.writeBooleanField("keepAlive", httpRequest.getKeepAlive());
        }
        if (httpRequest.getSecure() != null) {
            jgen.writeBooleanField("secure", httpRequest.getSecure());
        }
        if (httpRequest.getSocketAddress() != null) {
            jgen.writeObjectField("socketAddress", httpRequest.getSocketAddress());
        }
        if (httpRequest.getBody() != null) {
            jgen.writeObjectField("body", httpRequest.getBody());
        }
        jgen.writeEndObject();
    }
}
