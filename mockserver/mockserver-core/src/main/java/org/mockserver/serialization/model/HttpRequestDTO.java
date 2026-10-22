package org.mockserver.serialization.model;

import org.mockserver.model.*;

import java.util.List;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("UnusedReturnValue")
public class HttpRequestDTO extends RequestDefinitionDTO implements DTO<HttpRequest> {
    private NottableString method = string("");
    private NottableString path = string("");
    private Parameters pathParameters;
    private Parameters queryStringParameters;
    private BodyDTO body;
    private byte[] originalBody;
    private Cookies cookies;
    private Headers headers;
    private Boolean keepAlive;
    private Boolean secure;
    private Boolean respondBeforeBody;
    private Protocol protocol;
    private List<X509Certificate> clientCertificateChain;
    private ClientCertificate clientCertificate;
    private Jwt jwt;
    private NottableString clientIp;
    private SocketAddress socketAddress;
    private String localAddress;
    private String remoteAddress;

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
            originalBody = httpRequest.getOriginalBody();
            keepAlive = httpRequest.isKeepAlive();
            secure = httpRequest.isSecure();
            respondBeforeBody = httpRequest.getRespondBeforeBody();
            protocol = httpRequest.getProtocol();
            clientCertificateChain = httpRequest.getClientCertificateChain();
            clientCertificate = httpRequest.getClientCertificate();
            jwt = httpRequest.getJwt();
            clientIp = httpRequest.getClientIp();
            socketAddress = httpRequest.getSocketAddress();
            localAddress = httpRequest.getLocalAddress();
            remoteAddress = httpRequest.getRemoteAddress();
        }
    }

    public HttpRequest buildObject() {
        return (HttpRequest) new HttpRequest()
            .withMethod(method)
            .withPath(path)
            .withPathParameters(pathParameters)
            .withQueryStringParameters(queryStringParameters)
            .withBody((body != null ? Not.not(body.buildObject(), body.getNot()) : null))
            .withOriginalBody(originalBody)
            .withHeaders(headers)
            .withCookies(cookies)
            .withSecure(secure)
            .withRespondBeforeBody(respondBeforeBody)
            .withProtocol(protocol)
            .withKeepAlive(keepAlive)
            .withClientCertificateChain(clientCertificateChain)
            .withClientCertificate(clientCertificate)
            .withJwt(jwt)
            .withClientIp(clientIp)
            .withSocketAddress(socketAddress)
            .withLocalAddress(localAddress)
            .withRemoteAddress(remoteAddress)
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

    public byte[] getOriginalBody() {
        return originalBody;
    }

    public HttpRequestDTO setOriginalBody(byte[] originalBody) {
        this.originalBody = originalBody;
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

    public Boolean getRespondBeforeBody() {
        return respondBeforeBody;
    }

    public HttpRequestDTO setRespondBeforeBody(Boolean respondBeforeBody) {
        this.respondBeforeBody = respondBeforeBody;
        return this;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public HttpRequestDTO setProtocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public List<X509Certificate> getClientCertificateChain() {
        return clientCertificateChain;
    }

    public HttpRequestDTO setClientCertificateChain(List<X509Certificate> clientCertificateChain) {
        this.clientCertificateChain = clientCertificateChain;
        return this;
    }

    public ClientCertificate getClientCertificate() {
        return clientCertificate;
    }

    public HttpRequestDTO setClientCertificate(ClientCertificate clientCertificate) {
        this.clientCertificate = clientCertificate;
        return this;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public HttpRequestDTO setJwt(Jwt jwt) {
        this.jwt = jwt;
        return this;
    }

    public NottableString getClientIp() {
        return clientIp;
    }

    public HttpRequestDTO setClientIp(NottableString clientIp) {
        this.clientIp = clientIp;
        return this;
    }

    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public HttpRequestDTO setSocketAddress(SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
        return this;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public HttpRequestDTO setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
        return this;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public HttpRequestDTO setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
    }
}
