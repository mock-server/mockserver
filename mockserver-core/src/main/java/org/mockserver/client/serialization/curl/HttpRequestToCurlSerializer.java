package org.mockserver.client.serialization.curl;

import com.google.common.base.Strings;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.netty.codec.mappers.MockServerHttpRequestToFullHttpRequest;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;

/**
 * @author jamesdbloom
 */
public class HttpRequestToCurlSerializer {

    public String toCurl(HttpRequest httpRequest, @Nullable InetSocketAddress remoteAddress) {
        StringBuilder curlString = new StringBuilder();
        if (httpRequest != null) {
            if (!Strings.isNullOrEmpty(httpRequest.getFirstHeader(HOST.toString())) || remoteAddress != null) {
                boolean isSsl = httpRequest.isSecure() != null && httpRequest.isSecure();
                curlString.append("curl -v");
                curlString.append(" ");
                curlString.append("'");
                curlString.append((isSsl ? "https" : "http"));
                curlString.append("://");
                curlString.append(getHostAndPort(httpRequest, remoteAddress));
                curlString.append(getUri(httpRequest));
                curlString.append("'");
                if (!hasDefaultMethod(httpRequest)) {
                    curlString.append(" -X ").append(httpRequest.getMethod().getValue());
                }
                for (Header header : httpRequest.getHeaders()) {
                    for (NottableString headerValue : header.getValues()) {
                        curlString.append(" -H '").append(header.getName().getValue()).append(": ").append(headerValue.getValue()).append("'");
                        if (header.getName().getValue().toLowerCase().contains("Accept-Encoding".toLowerCase())) {
                            if (headerValue.getValue().toLowerCase().contains("gzip")
                                    || headerValue.getValue().toLowerCase().contains("deflate")
                                    || headerValue.getValue().toLowerCase().contains("sdch")) {
                                curlString.append(" ");
                                curlString.append("--compress");
                            }
                        }
                    }
                }
                curlString.append(getCookieHeader(httpRequest));
            } else {
                curlString.append("no host header or remote address specified");
            }
        } else {
            curlString.append("null HttpRequest");
        }
        return curlString.toString();
    }

    private boolean hasDefaultMethod(HttpRequest httpRequest) {
        return Strings.isNullOrEmpty(httpRequest.getMethod().getValue()) || httpRequest.getMethod().getValue().equalsIgnoreCase("GET");
    }

    private String getUri(HttpRequest outboundHttpRequest) {
        String uri = new MockServerHttpRequestToFullHttpRequest().getURI(outboundHttpRequest);
        if (Strings.isNullOrEmpty(uri)) {
            uri = "/";
        } else if (!StringUtils.startsWith(uri, "/")) {
            uri = "/" + uri;
        }
        return uri;
    }

    private String getHostAndPort(HttpRequest httpRequest, InetSocketAddress remoteAddress) {
        String host = httpRequest.getFirstHeader("Host");
        if (Strings.isNullOrEmpty(host)) {
            host = remoteAddress.getHostName() + ":" + remoteAddress.getPort();
        }
        return host;
    }

    private String getCookieHeader(HttpRequest httpRequest) {
        List<Cookie> cookies = new ArrayList<Cookie>();
        for (org.mockserver.model.Cookie cookie : httpRequest.getCookies()) {
            cookies.add(new DefaultCookie(cookie.getName().getValue(), cookie.getValue().getValue()));
        }
        if (cookies.size() > 0) {
            return " -H '" + COOKIE + ": " + ClientCookieEncoder.LAX.encode(cookies) + "'";
        } else {
            return "";
        }
    }
}
