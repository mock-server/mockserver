package org.mockserver.mappers.jetty;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpClientResponseMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpResponse mapHttpClientResponseToHttpResponse(CloseableHttpResponse clientResponse) throws IOException {
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
        Multimap<String, String> headerMap = LinkedListMultimap.create();
        for (org.apache.http.Header header : clientResponse.getAllHeaders()) {
            headerMap.put(header.getName(), header.getValue());
        }
        List<Header> headers = new ArrayList<Header>();
        for (String header : headerMap.keySet()) {
            headers.add(new Header(header, headerMap.get(header)));
        }
        List<String> headersToRemove = Arrays.asList("Content-Encoding", "Content-Length", "Transfer-Encoding");
        for (Header header : new ArrayList<Header>(headers)) {
            if (headersToRemove.contains(header.getName())) {
                headers.remove(header);
            }
        }
        httpResponse.withHeaders(headers);
    }

    private void setCookies(HttpResponse httpResponse) {
        List<Cookie> mappedCookies = new ArrayList<Cookie>();
        for (Header header : httpResponse.getHeaders()) {
            if (header.getName().equals("Cookie") || header.getName().equals("Set-Cookie")) {
                for (String cookieHeader : header.getValues()) {
                    try {
                        for (HttpCookie httpCookie : HttpCookie.parse(cookieHeader)) {
                            mappedCookies.add(new Cookie(httpCookie.getName(), httpCookie.getValue()));
                        }
                    } catch (IllegalArgumentException iae) {
                        logger.warn("Exception while parsing cookie header [" + cookieHeader + "]", iae);
                    }
                }
            }
        }
        httpResponse.withCookies(mappedCookies);
    }

    private void setBody(HttpResponse httpResponse, byte[] content) {
        httpResponse.withBody(content);
    }
}
