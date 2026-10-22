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

    private static final long serialVersionUID = 1L;

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
        if (httpRequest.getPathParameters() != null && !httpRequest.getPathParameters().isEmpty()) {
            jgen.writeObjectField("pathParameters", httpRequest.getPathParameters());
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
        if (httpRequest.getRespondBeforeBody() != null) {
            jgen.writeBooleanField("respondBeforeBody", httpRequest.getRespondBeforeBody());
        }
        if (httpRequest.getClientCertificateChain() != null && !httpRequest.getClientCertificateChain().isEmpty()) {
            jgen.writeObjectField("clientCertificateChain", httpRequest.getClientCertificateChain());
        }
        if (httpRequest.getClientCertificate() != null) {
            jgen.writeObjectField("clientCertificate", httpRequest.getClientCertificate());
        }
        if (httpRequest.getJwt() != null) {
            jgen.writeObjectField("jwt", httpRequest.getJwt());
        }
        if (httpRequest.getClientIp() != null) {
            jgen.writeObjectField("clientIp", httpRequest.getClientIp());
        }
        if (httpRequest.getSocketAddress() != null) {
            jgen.writeObjectField("socketAddress", httpRequest.getSocketAddress());
        }
        if (httpRequest.getProtocol() != null) {
            jgen.writeStringField("protocol", httpRequest.getProtocol().name());
        }
        if (isNotBlank(httpRequest.getLocalAddress())) {
            jgen.writeObjectField("localAddress", httpRequest.getLocalAddress());
        }
        if (isNotBlank(httpRequest.getRemoteAddress())) {
            jgen.writeObjectField("remoteAddress", httpRequest.getRemoteAddress());
        }
        if (httpRequest.getBody() != null) {
            // use defaultSerializeField (not jgen.writeObjectField) so the active SerializerProvider - and its
            // per-call "emitRawBytes" attribute set on the recorded-request retrieval path (#2374) - is propagated
            // to the nested body serializer; jgen.writeObjectField resolves the serializer via the generator's codec
            // and would drop the attribute
            provider.defaultSerializeField("body", httpRequest.getBody(), jgen);
        }
        if (httpRequest.getOriginalBody() != null && httpRequest.getOriginalBody().length > 0) {
            jgen.writeBinaryField("originalBody", httpRequest.getOriginalBody());
        }
        jgen.writeEndObject();
    }
}
