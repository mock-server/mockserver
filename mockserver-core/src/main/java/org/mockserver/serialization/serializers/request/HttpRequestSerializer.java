package org.mockserver.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.HttpRequest;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
        if (httpRequest.getMethod() != null && !httpRequest.getMethod().isBlank()) {
            jgen.writeObjectField("method", httpRequest.getMethod());
        }
        if (httpRequest.getPath() != null && !httpRequest.getPath().isBlank()) {
            jgen.writeObjectField("path", httpRequest.getPath());
        }
        if (httpRequest.getPathParameters() != null && !httpRequest.getPathParameters().isEmpty()) {
            jgen.writeObjectField("pathParameters", httpRequest.getPathParameters());
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
        if (httpRequest.getClientCertificateChain() != null && !httpRequest.getClientCertificateChain().isEmpty()) {
            jgen.writeObjectField("clientCertificateChain", httpRequest.getClientCertificateChain());
        }
        if (httpRequest.getSocketAddress() != null) {
            jgen.writeObjectField("socketAddress", httpRequest.getSocketAddress());
        }
        if (isNotBlank(httpRequest.getRemoteAddress())) {
            jgen.writeObjectField("remoteAddress", httpRequest.getRemoteAddress());
        }
        if (httpRequest.getBody() != null && isNotBlank(String.valueOf(httpRequest.getBody().getValue()))) {
            jgen.writeObjectField("body", httpRequest.getBody());
        }
        jgen.writeEndObject();
    }
}
