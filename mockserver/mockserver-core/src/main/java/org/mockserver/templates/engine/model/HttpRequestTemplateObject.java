package org.mockserver.templates.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.BodyDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author jamesdbloom
 */
public class HttpRequestTemplateObject extends RequestDefinition {
    private int hashCode;
    private String method = "";
    private String path = "";
    private final Map<String, List<String>> pathParameters = new HashMap<>();
    private final Map<String, List<String>> queryStringParameters = new HashMap<>();
    private final Map<String, String> cookies = new HashMap<>();
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private BodyDTO body = null;
    private Boolean secure = null;
    private List<X509Certificate> clientCertificateChain = null;
    private String localAddress = null;
    private String remoteAddress = null;
    private Boolean keepAlive = null;
    private String jsonRpcId = null;
    private String jsonRpcRawId = null;
    private String jsonRpcMethod = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestTemplateObject.class);
    private static final ObjectMapper JSON_OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public HttpRequestTemplateObject(HttpRequest httpRequest) {
        if (httpRequest != null) {
            method = httpRequest.getMethod().getValue();
            path = httpRequest.getPath().getValue();
            for (Parameter parameter : httpRequest.getPathParameterList()) {
                pathParameters.put(parameter.getName().getValue(), parameter.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
            }
            for (Parameter parameter : httpRequest.getQueryStringParameterList()) {
                queryStringParameters.put(parameter.getName().getValue(), parameter.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
            }
            for (Header header : httpRequest.getHeaderList()) {
                headers.put(header.getName().getValue(), header.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
            }
            for (Cookie cookie : httpRequest.getCookieList()) {
                cookies.put(cookie.getName().getValue(), cookie.getValue().getValue());
            }
            body = BodyDTO.createDTO(httpRequest.getBody());
            secure = httpRequest.isSecure();
            clientCertificateChain = httpRequest.getClientCertificateChain();
            localAddress = httpRequest.getLocalAddress();
            remoteAddress = httpRequest.getRemoteAddress();
            keepAlive = httpRequest.isKeepAlive();
            setNot(httpRequest.getNot());
            extractJsonRpcFields(httpRequest);
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, List<String>> getPathParameters() {
        return pathParameters;
    }

    public Map<String, List<String>> getQueryStringParameters() {
        return queryStringParameters;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public String getBody() {
        return BodyDTO.toString(body);
    }

    @Deprecated
    public String getBodyAsString() {
        return BodyDTO.toString(body);
    }

    public Boolean getSecure() {
        return secure;
    }

    public List<X509Certificate> getClientCertificateChain() {
        return clientCertificateChain;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public String getJsonRpcId() {
        return jsonRpcId;
    }

    public String getJsonRpcRawId() {
        return jsonRpcRawId;
    }

    public String getJsonRpcMethod() {
        return jsonRpcMethod;
    }

    private void extractJsonRpcFields(HttpRequest httpRequest) {
        try {
            String bodyString = httpRequest.getBodyAsString();
            if (bodyString != null && !bodyString.isEmpty() && bodyString.trim().startsWith("{")) {
                JsonNode root = JSON_OBJECT_MAPPER.readTree(bodyString);
                if (root.has("jsonrpc") && root.has("method")) {
                    JsonNode idNode = root.get("id");
                    if (idNode != null) {
                        jsonRpcId = idNode.isTextual() ? idNode.asText() : idNode.toString();
                        jsonRpcRawId = idNode.toString();
                    }
                    JsonNode methodNode = root.get("method");
                    if (methodNode != null) {
                        jsonRpcMethod = methodNode.asText();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.trace("failed to extract JSON-RPC fields from request body", e);
        }
    }

    public HttpRequestTemplateObject shallowClone() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        HttpRequestTemplateObject that = (HttpRequestTemplateObject) o;
        return Objects.equals(method, that.method) &&
            Objects.equals(path, that.path) &&
            Objects.equals(pathParameters, that.pathParameters) &&
            Objects.equals(queryStringParameters, that.queryStringParameters) &&
            Objects.equals(cookies, that.cookies) &&
            Objects.equals(headers, that.headers) &&
            Objects.equals(body, that.body) &&
            Objects.equals(secure, that.secure) &&
            Objects.equals(localAddress, that.localAddress) &&
            Objects.equals(remoteAddress, that.remoteAddress) &&
            Objects.equals(keepAlive, that.keepAlive) &&
            Objects.equals(jsonRpcId, that.jsonRpcId) &&
            Objects.equals(jsonRpcRawId, that.jsonRpcRawId) &&
            Objects.equals(jsonRpcMethod, that.jsonRpcMethod);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), method, path, pathParameters, queryStringParameters, cookies, headers, body, secure, localAddress, remoteAddress, keepAlive, jsonRpcId, jsonRpcRawId, jsonRpcMethod);
        }
        return hashCode;
    }
}
