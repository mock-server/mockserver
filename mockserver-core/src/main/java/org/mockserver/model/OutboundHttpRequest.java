package org.mockserver.model;

import com.google.common.base.Strings;
import org.mockserver.socket.SSLFactory;

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
        if (httpRequest != null) {
            if (inetSocketAddress == null) {
                if (!Strings.isNullOrEmpty(httpRequest.getFirstHeader("Host"))) {
                    // read remote socket from host header for HTTP proxy
                    String[] hostHeaderParts = httpRequest.getFirstHeader("Host").split(":");

                    Integer port = (httpRequest.isSecure() ? 443 : 80); // default port
                    if (hostHeaderParts.length > 1) {
                        port = Integer.parseInt(hostHeaderParts[1]);    // non-default port
                    }

                    // add Subject Alternative Name for SSL certificate (just in case this hasn't been added before)
                    SSLFactory.addSubjectAlternativeName(hostHeaderParts[0]);

                    inetSocketAddress = new InetSocketAddress(hostHeaderParts[0], port);
                } else {
                    throw new IllegalArgumentException("Host header must be provided for requests being forwarded, the following request does not include the \"Host\" header:" + System.getProperty("line.separator") + httpRequest);
                }
            }

            return outboundRequest(inetSocketAddress.getHostName(), inetSocketAddress.getPort(), contextPath, httpRequest);
        }
        return null;
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
        } else if (secure && port == 80) {
            port = 443;
        }
        super.setSecure(secure);
        return this;
    }
}
