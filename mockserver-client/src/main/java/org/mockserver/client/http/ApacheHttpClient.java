package org.mockserver.client.http;

import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.mockserver.mappers.jetty.HttpClientResponseMapper;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.mockserver.configuration.SystemProperties.maxTimeout;

/**
 * @author jamesdbloom
 */
public class ApacheHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(ApacheHttpClient.class);
    private static final HttpClientResponseMapper httpClientResponseMapper = new HttpClientResponseMapper();
    private final CloseableHttpClient httpClient;

    public ApacheHttpClient() {
        try {
            this.httpClient = HttpClients
                    .custom()
                    .setSslcontext(
                            SSLContexts
                                    .custom()
                                    .loadTrustMaterial(SSLFactory.buildKeyStore(), new TrustStrategy() {
                                        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                                            return true;
                                        }
                                    })
                                    .build()
                    )
                    .setHostnameVerifier(new AllowAllHostnameVerifier())
                    .setDefaultRequestConfig(
                            RequestConfig
                                    .custom()
                                    .setConnectionRequestTimeout(new Long(maxTimeout()).intValue())
                                    .setConnectTimeout(new Long(maxTimeout()).intValue())
                                    .build()
                    )
                    .disableCookieManagement()
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Exception creating http client", e);
        }
    }

    public String sendPUTRequest(String baseUri, String path, String body) {
        try {
            if (baseUri.endsWith("/") && path.startsWith("/")) {
                path = StringUtils.substringAfter(path, "/");
            } else if (!baseUri.endsWith("/") && !path.startsWith("/")) {
                baseUri += "/";
            }
            HttpPut httpPut = new HttpPut(baseUri + path);
            httpPut.setEntity(new StringEntity(body, Charsets.UTF_8));
            return new String(EntityUtils.toByteArray(httpClient.execute(httpPut).getEntity()), Charsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to [" + baseUri + path + "] with body [" + body + "]", e);
        }
    }

    public HttpResponse sendRequest(HttpRequest httpRequest) {
        try {
            URI url = new URI(URLEncoder.encodeURL(httpRequest.getURL()));
            HttpMethod httpMethod = HttpMethod.parseString(httpRequest.getMethod());
            HttpUriRequest proxiedRequest = createHttpUriRequest(httpMethod, url);

            for (Header header : httpRequest.getHeaders()) {
                String headerName = header.getName();
                if (!headerName.equalsIgnoreCase(HTTP.CONTENT_LEN) && !headerName.equalsIgnoreCase(HTTP.TRANSFER_ENCODING)) {
                    for (String headerValue : header.getValues()) {
                        proxiedRequest.addHeader(headerName, headerValue);
                    }
                }
            }
            if (httpMethod == HttpMethod.POST && proxiedRequest.getHeaders(HttpHeaders.CONTENT_TYPE).length == 0) {
                // handle missing header which causes error with IIS
                proxiedRequest.addHeader(HttpHeaders.CONTENT_TYPE, URLEncodedUtils.CONTENT_TYPE);
            }

            BrowserCompatSpec browserCompatSpec = new BrowserCompatSpec();
            List<org.apache.http.cookie.Cookie> cookies = new ArrayList<org.apache.http.cookie.Cookie>();
            for (Cookie cookie : httpRequest.getCookies()) {
                for (String value : cookie.getValues()) {
                    cookies.add(new BasicClientCookie(cookie.getName(), value));
                }
            }
            if (cookies.size() > 0) {
                for (org.apache.http.Header header : browserCompatSpec.formatCookies(cookies)) {
                    proxiedRequest.addHeader(header);
                }
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Proxy sending request:\n" + new ObjectMapper()
                        .setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT)
                        .setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL)
                        .setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY)
                        .configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false)
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(httpRequest));
            }
            if (proxiedRequest instanceof HttpEntityEnclosingRequest) {
                ((HttpEntityEnclosingRequest) proxiedRequest).setEntity(new StringEntity(httpRequest.getBody()));
            }
            return httpClientResponseMapper.mapHttpClientResponseToHttpResponse(this.httpClient.execute(proxiedRequest));
        } catch (Exception ioe) {
            throw new RuntimeException("IOException while sending PUT request", ioe);
        }
    }

    protected HttpUriRequest createHttpUriRequest(HttpMethod httpMethod, URI uri) {
        switch (httpMethod) {
            case GET:
                return new HttpGet(uri);
            case DELETE:
                return new HttpDelete(uri);
            case HEAD:
                return new HttpHead(uri);
            case OPTIONS:
                return new HttpOptions(uri);
            case POST:
                return new HttpPost(uri);
            case PUT:
                return new HttpPut(uri);
            case TRACE:
                return new HttpTrace(uri);
            case PATCH:
                return new HttpPatch(uri);
            default:
                throw new IllegalArgumentException("Invalid HTTP method: " + httpMethod);
        }
    }

    public enum HttpMethod {

        GET, POST, HEAD, OPTIONS, PUT, PATCH, DELETE, TRACE;

        public static HttpMethod parseString(String string) {
            try {
                return valueOf(string);
            } catch (IllegalArgumentException iae) {
                logger.trace("Not match found for http method [" + string + "]", iae);
            }
            return GET;
        }

    }
}
