package org.mockserver.client.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.base.Strings;
import org.mockserver.model.HttpRequest;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializer extends StdSerializer<HttpRequest> {

    public HttpRequestSerializer() {
        super(HttpRequest.class);
    }

    @Override
    public void serialize(HttpRequest httpRequest, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (httpRequest.getNot() != null && httpRequest.getNot()) {
            jgen.writeBooleanField("not", httpRequest.getNot());
        }
        if (httpRequest.getMethod() != null && !Strings.isNullOrEmpty(httpRequest.getMethod().getValue())) {
            jgen.writeObjectField("method", httpRequest.getMethod());
        }
        if (httpRequest.getPath() != null && !Strings.isNullOrEmpty(httpRequest.getPath().getValue())) {
            jgen.writeObjectField("path", httpRequest.getPath());
        }
        if (httpRequest.getQueryStringParameterList() != null && !httpRequest.getQueryStringParameterList().isEmpty()) {
            jgen.writeObjectField("queryStringParameters", httpRequest.getQueryStringParameters());
        }
        if (httpRequest.getHeaderList() != null && !httpRequest.getHeaderList().isEmpty()) {
            jgen.writeObjectField("headers", httpRequest.getHeaders());
        }
        if (httpRequest.getCookieList() != null && !httpRequest.getCookieList().isEmpty()) {
            jgen.writeObjectField("cookies", httpRequest.getCookies());
        }
        if (httpRequest.isKeepAlive() != null) {
            jgen.writeBooleanField("keepAlive", httpRequest.isKeepAlive());
        }
        if (httpRequest.isSecure() != null) {
            jgen.writeBooleanField("secure", httpRequest.isSecure());
        }
        if (httpRequest.getBody() != null && !Strings.isNullOrEmpty(String.valueOf(httpRequest.getBody().getValue()))) {
            jgen.writeObjectField("body", httpRequest.getBody());
        }
        jgen.writeEndObject();
    }
}
