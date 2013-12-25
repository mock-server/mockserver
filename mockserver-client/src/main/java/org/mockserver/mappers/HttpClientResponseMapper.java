package org.mockserver.mappers;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpField;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpClientResponseMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpResponse buildHttpResponse(Response clientResponse, byte[] content) {
        HttpResponse httpResponse = new HttpResponse();
        setStatusCode(httpResponse, clientResponse);
        setHeaders(httpResponse, clientResponse);
        setCookies(httpResponse);
        setBody(httpResponse, content);
        return httpResponse;
    }

    private void setStatusCode(HttpResponse httpResponse, Response clientResponse) {
        httpResponse.withStatusCode(clientResponse.getStatus());
    }

    private void setBody(HttpResponse httpResponse, byte[] content) {
        httpResponse.withBody(content);
    }

    private void setHeaders(HttpResponse httpResponse, Response clientResponse) {
        Multimap<String, String> headerMap = LinkedListMultimap.create();
        for (HttpField httpField : clientResponse.getHeaders()) {
            headerMap.put(httpField.getName(), httpField.getValue());
        }
        List<Header> headers = new ArrayList<>();
        for (String header : headerMap.keys()) {
            headers.add(new Header(header, headerMap.get(header)));
        }
        List<String> headersToRemove = Arrays.asList("Content-Encoding", "Content-Length", "Transfer-Encoding");
        for (Header header : new ArrayList<>(headers)) {
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
}
