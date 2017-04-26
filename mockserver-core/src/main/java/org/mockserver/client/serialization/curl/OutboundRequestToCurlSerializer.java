package org.mockserver.client.serialization.curl;

import com.google.common.base.Strings;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.netty.codec.mappers.MockServerOutboundHttpRequestToFullHttpRequest;
import org.mockserver.model.Header;
import org.mockserver.model.NottableString;
import org.mockserver.model.OutboundHttpRequest;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;

/**
 * @author jamesdbloom
 */
public class OutboundRequestToCurlSerializer {

    public String toCurl(OutboundHttpRequest outboundHttpRequest) {
        StringBuilder curlString = new StringBuilder();
        if (outboundHttpRequest != null) {
            boolean isSsl = outboundHttpRequest.isSecure() != null && outboundHttpRequest.isSecure();
            curlString.append("curl -v");
            curlString.append(" ");
            curlString.append("'");
            curlString.append((isSsl ? "https" : "http"));
            curlString.append("://");
            curlString.append(getHostAndPort(outboundHttpRequest));
            curlString.append(getUri(outboundHttpRequest));
            curlString.append("'");
            if (!hasDefaultMethod(outboundHttpRequest)) {
                curlString.append(" -X ").append(outboundHttpRequest.getMethod().getValue());
            }
            for (Header header : outboundHttpRequest.getHeaders()) {
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
            curlString.append(getCookieHeader(outboundHttpRequest));
        }
        return curlString.toString();
    }

    private boolean hasDefaultMethod(OutboundHttpRequest outboundHttpRequest) {
        return Strings.isNullOrEmpty(outboundHttpRequest.getMethod().getValue()) || outboundHttpRequest.getMethod().getValue().equalsIgnoreCase("GET");
    }

    private String getUri(OutboundHttpRequest outboundHttpRequest) {
        String uri = new MockServerOutboundHttpRequestToFullHttpRequest().getURI(outboundHttpRequest);
        if (Strings.isNullOrEmpty(uri)) {
            uri = "/";
        } else if (!StringUtils.startsWith(uri, "/")) {
            uri = "/" + uri;
        }
        return uri;
    }

    private String getHostAndPort(OutboundHttpRequest outboundHttpRequest) {
        String host = outboundHttpRequest.getFirstHeader("Host");
        if (Strings.isNullOrEmpty(host)) {
            host = outboundHttpRequest.getDestination().getHostName() + ":" + outboundHttpRequest.getDestination().getPort();
        }
        return host;
    }

    private String getCookieHeader(OutboundHttpRequest outboundHttpRequest) {
        List<Cookie> cookies = new ArrayList<Cookie>();
        for (org.mockserver.model.Cookie cookie : outboundHttpRequest.getCookies()) {
            cookies.add(new DefaultCookie(cookie.getName().getValue(), cookie.getValue().getValue()));
        }
        if (cookies.size() > 0) {
            return " -H '" + COOKIE + ": " + ClientCookieEncoder.LAX.encode(cookies) + "'";
        } else {
            return "";
        }
    }
}
