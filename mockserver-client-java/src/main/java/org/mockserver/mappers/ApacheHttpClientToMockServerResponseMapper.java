package org.mockserver.mappers;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.*;

/**
 * @author jamesdbloom
 */
public class ApacheHttpClientToMockServerResponseMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpResponse mapApacheHttpClientResponseToMockServerResponse(CloseableHttpResponse clientResponse) throws IOException {
        HttpResponse httpResponse = new HttpResponse();
        setStatusCode(httpResponse, clientResponse);
        setHeaders(httpResponse, clientResponse);
        setCookies(httpResponse);
        if (clientResponse.getEntity() != null) {
            setBody(httpResponse, EntityUtils.toByteArray(clientResponse.getEntity()));
        }
        return httpResponse;
    }

    private void setStatusCode(HttpResponse httpResponse, CloseableHttpResponse clientResponse) {
        if (clientResponse.getStatusLine() != null) {
            httpResponse.withStatusCode(clientResponse.getStatusLine().getStatusCode());
        }
    }

    private void setHeaders(HttpResponse httpResponse, CloseableHttpResponse clientResponse) {
        Map<String, Header> mappedHeaders = new HashMap<String, Header>();
        for (org.apache.http.Header header : clientResponse.getAllHeaders()) {
            if (mappedHeaders.containsKey(header.getName())) {
                mappedHeaders.get(header.getName()).addValue(header.getValue());
            } else {
                mappedHeaders.put(header.getName(), new Header(header.getName(), header.getValue()));
            }
        }
        List<Header> headers = new ArrayList<Header>(mappedHeaders.values());
        List<String> headersToRemove = Arrays.asList("Content-Encoding", "Content-Length", "Transfer-Encoding");
        for (Header header : new ArrayList<Header>(headers)) {
            if (headersToRemove.contains(header.getName())) {
                headers.remove(header);
            }
        }
        httpResponse.withHeaders(headers);
    }

    private void setCookies(HttpResponse httpResponse) {
        Map<String, Cookie> mappedCookies = new HashMap<String, Cookie>();
        for (Header header : httpResponse.getHeaders()) {
            if (header.getName().equals("Cookie") || header.getName().equals("Set-Cookie")) {
                for (String cookieHeader : header.getValues()) {
                    try {
                        for (HttpCookie httpCookie : HttpCookie.parse(cookieHeader)) {
                            if (mappedCookies.containsKey(httpCookie.getName())) {
                                mappedCookies.get(httpCookie.getName()).addValue(httpCookie.getValue());
                            } else {
                                mappedCookies.put(httpCookie.getName(), new Cookie(httpCookie.getName(), httpCookie.getValue()));
                            }
                        }
                    } catch (IllegalArgumentException iae) {
                        logger.warn("Exception while parsing cookie header [" + cookieHeader + "]", iae);
                    }
                }
            }
        }
        httpResponse.withCookies(new ArrayList<Cookie>(mappedCookies.values()));
    }

    private void setBody(HttpResponse httpResponse, byte[] content) {
        httpResponse.withBody(content);
    }
}
