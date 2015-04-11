package org.mockserver.model;

import java.net.InetSocketAddress;

/**
 * @author jamesdbloom
 */
public class OutboundHttpRequest extends HttpRequest {

    private final InetSocketAddress destination;
    private final String contextPath;

    public OutboundHttpRequest(InetSocketAddress destination, String contextPath, HttpRequest httpRequest) {
        this.destination = destination;
        this.contextPath = contextPath;
        this.secure = httpRequest.secure;
        this.method = httpRequest.method;
        this.path = httpRequest.path;
        this.queryStringParameters = httpRequest.queryStringParameters;
        this.body = httpRequest.body;
        this.headers = httpRequest.headers;
        this.cookies = httpRequest.cookies;
        this.isKeepAlive = httpRequest.isKeepAlive;
    }

    public static OutboundHttpRequest outboundRequest(String host, int port, String contextPath, HttpRequest httpRequest) {
        return new OutboundHttpRequest(new InetSocketAddress(host, port), contextPath, httpRequest);
    }

    public static OutboundHttpRequest outboundRequest(InetSocketAddress destination, String contextPath, HttpRequest httpRequest) {
        return new OutboundHttpRequest(destination, contextPath, httpRequest);
    }

    public InetSocketAddress getDestination() {
        return destination;
    }

    public String getContextPath() {
        return contextPath;
    }
}
