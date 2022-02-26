package org.mockserver.serialization.model;

import org.mockserver.model.*;

import java.util.List;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HttpRequestDTO extends RequestDefinitionDTO implements DTO<HttpRequest> {
    private NottableString method = string("");
    private NottableString path = string("");
    private Parameters pathParameters;
    private Parameters queryStringParameters;
    private BodyDTO body;
    private Cookies cookies;
    private Headers headers;
    private Boolean keepAlive;
    private Boolean secure;
    private List<X509Certificate> clientCertificateChain;
    private SocketAddress socketAddress;

    public HttpRequestDTO() {
        super(null);
    }

    public HttpRequestDTO(HttpRequest httpRequest) {
        super(httpRequest != null ? httpRequest.getNot() : null);
        if (httpRequest != null) {
            method = httpRequest.getMethod();
            path = httpRequest.getPath();
            headers = httpRequest.getHeaders();
            cookies = httpRequest.getCookies();
            pathParameters = httpRequest.getPathParameters();
            queryStringParameters = httpRequest.getQueryStringParameters();
            body = BodyDTO.createDTO(httpRequest.getBody());
            keepAlive = httpRequest.isKeepAlive();
            secure = httpRequest.isSecure();
            clientCertificateChain = httpRequest.getClientCertificateChain();
            socketAddress = httpRequest.getSocketAddress();
        }
    }

    public HttpRequest buildObject() {
        return (HttpRequest) new HttpRequest()
            .withMethod(method)
            .withPath(path)
            .withPathParameters(pathParameters)
            .withQueryStringParameters(queryStringParameters)
            .withBody((body != null ? Not.not(body.buildObject(), body.getNot()) : null))
            .withHeaders(headers)
            .withCookies(cookies)
            .withSecure(secure)
            .withKeepAlive(keepAlive)
            .withClientCertificateChain(clientCertificateChain)
            .withSocketAddress(socketAddress)
            .withNot(getNot());
    }

    public NottableString getMethod() {
        return method;
    }

    public HttpRequestDTO setMethod(NottableString method) {
        this.method = method;
        return this;
    }

    public NottableString getPath() {
        return path;
    }

    public HttpRequestDTO setPath(NottableString path) {
        this.path = path;
        return this;
    }

    public Parameters getPathParameters() {
        return pathParameters;
    }

    public HttpRequestDTO setPathParameters(Parameters pathParameters) {
        this.pathParameters = pathParameters;
        return this;
    }

    public Parameters getQueryStringParameters() {
        return queryStringParameters;
    }

    public HttpRequestDTO setQueryStringParameters(Parameters queryStringParameters) {
        this.queryStringParameters = queryStringParameters;
        return this;
    }

    public BodyDTO getBody() {
        return body;
    }

    public HttpRequestDTO setBody(BodyDTO body) {
        this.body = body;
        return this;
    }

    public Headers getHeaders() {
        return headers;
    }

    public HttpRequestDTO setHeaders(Headers headers) {
        this.headers = headers;
        return this;
    }

    public Cookies getCookies() {
        return cookies;
    }

    public HttpRequestDTO setCookies(Cookies cookies) {
        this.cookies = cookies;
        return this;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public HttpRequestDTO setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public Boolean getSecure() {
        return secure;
    }

    public HttpRequestDTO setSecure(Boolean secure) {
        this.secure = secure;
        return this;
    }

    public List<X509Certificate> getClientCertificateChain() {
        return clientCertificateChain;
    }

    public HttpRequestDTO setClientCertificateChain(List<X509Certificate> clientCertificateChain) {
        this.clientCertificateChain = clientCertificateChain;
        return this;
    }

    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public HttpRequestDTO setSocketAddress(SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
        return this;
    }
}
