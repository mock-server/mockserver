package org.mockserver.client.http;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class HttpRequestClient {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String baseUri;
    private HttpClient httpClient = new HttpClient(new SslContextFactory(true));

    public HttpRequestClient(String baseUri) {
        if (baseUri.endsWith("/")) throw new IllegalArgumentException("Base URL [" + baseUri + "] should not have a trailing slash");
        this.baseUri = baseUri;
        try {
            this.httpClient.start();
        } catch (Exception e) {
            logger.error("Exception starting HttpClient", e);
            throw new RuntimeException("Exception starting HttpClient", e);
        }
    }

    @VisibleForTesting
    HttpRequestClient(String baseUri, HttpClient httpClient) {
        if (baseUri.endsWith("/")) throw new IllegalArgumentException("Base URL [" + baseUri + "] should not have a trailing slash");
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        try {
            this.httpClient.start();
        } catch (Exception e) {
            logger.error("Exception starting HttpClient", e);
            throw new RuntimeException("Exception starting HttpClient", e);
        }
    }

    public void sendRequest(String body, String path) {
        try {
            httpClient.newRequest(baseUri + path)
                    .method(HttpMethod.PUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .content(new ComparableStringContentProvider(body, "UTF-8"))
                    .send();
        } catch (Exception e) {
            logger.error("Exception sending request to [" + path + "] with body [" + body + "]", e);
        }
    }

    public ContentResponse sendRequest(HttpRequest httpRequest) {
        try {
            String url = httpRequest.getURL();
            if (Strings.isNullOrEmpty(url)) {
                url = baseUri + httpRequest.getPath() + (Strings.isNullOrEmpty(httpRequest.getQueryString()) ? "" : '?' + httpRequest.getQueryString());
            }

            Request request = httpClient
                    .newRequest(url)
                    .method(HttpMethod.fromString(httpRequest.getMethod()))
                    .content(new BytesContentProvider(httpRequest.getBody().getBytes()));
            for (Header header : httpRequest.getHeaders()) {
                for (String value : header.getValues()) {
                    request.header(header.getName(), value);
                }
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (Cookie cookie : httpRequest.getCookies()) {
                for (String value : cookie.getValues()) {
                    stringBuilder.append(cookie.getName()).append("=").append(value).append("; ");
                }
            }
            if (stringBuilder.length() > 0) {
                request.header("Cookie", stringBuilder.toString());
            }
            return request.send();
        } catch (Exception e) {
            throw new RuntimeException("Exception sending request to [" + httpRequest.getURL() + "] with body [" + httpRequest.getBody() + "]", e);
        }
    }
}
