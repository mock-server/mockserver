package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class OutboundHttpRequest extends HttpRequest {

    private final String host;
    private final Integer port;

    public OutboundHttpRequest(String host, Integer port, HttpRequest httpRequest) {
        this.host = host;
        this.port = port;
        this.secure = httpRequest.secure;
        this.method = httpRequest.method;
        this.path = httpRequest.path;
        this.queryStringParameters = httpRequest.queryStringParameters;
        this.body = httpRequest.body;
        this.headers = httpRequest.headers;
        this.cookies = httpRequest.cookies;
        this.isKeepAlive = httpRequest.isKeepAlive;
    }

    public static OutboundHttpRequest outboundRequest(String host, Integer port, HttpRequest httpRequest) {
        return new OutboundHttpRequest(host, port, httpRequest);
    }


    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

}
