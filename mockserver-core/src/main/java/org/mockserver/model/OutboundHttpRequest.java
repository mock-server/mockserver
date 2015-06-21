package org.mockserver.model;

import com.google.common.base.Strings;

import java.net.InetSocketAddress;

/**
 * @author jamesdbloom
 */
public class OutboundHttpRequest extends HttpRequest {

    private String hostname;
    private int port;
    private String contextPath;

    public OutboundHttpRequest(String hostname, int port, String contextPath, HttpRequest httpRequest) {
        this.hostname = hostname;
        this.port = port;
        this.contextPath = Strings.nullToEmpty(contextPath);
        this.secure = httpRequest.secure;
        this.method = httpRequest.method;
        this.path = httpRequest.path;
        this.queryStringParameters = httpRequest.queryStringParameters;
        this.body = httpRequest.body;
        this.headers = httpRequest.headers;
        this.cookies = httpRequest.cookies;
        this.isKeepAlive = httpRequest.isKeepAlive;
    }

    public static OutboundHttpRequest outboundRequest(InetSocketAddress inetSocketAddress, String contextPath, HttpRequest httpRequest) {
        return outboundRequest(inetSocketAddress.getHostName(), inetSocketAddress.getPort(), contextPath, httpRequest);
    }

    public static OutboundHttpRequest outboundRequest(String hostname, int port, String contextPath, HttpRequest httpRequest) {
        return new OutboundHttpRequest(hostname, port, contextPath, httpRequest);
    }

    public InetSocketAddress getDestination() {
        return new InetSocketAddress(hostname, port);
    }

    public String getContextPath() {
        return contextPath;
    }

    public OutboundHttpRequest setSecure(boolean secure) {
        if (!secure && port == 443) {
            port = 80;
        }
        super.setSecure(secure);
        return this;
    }
}
