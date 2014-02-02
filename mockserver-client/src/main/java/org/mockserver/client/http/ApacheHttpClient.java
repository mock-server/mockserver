package org.mockserver.client.http;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
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
import org.mockserver.mappers.ApacheHttpClientToMockServerResponseMapper;
import org.mockserver.model.*;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
    private ApacheHttpClientToMockServerResponseMapper apacheHttpClientToMockServerResponseMapper = new ApacheHttpClientToMockServerResponseMapper();
    private CloseableHttpClient httpClient;

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
            // url & method
            URI url = buildUrl(httpRequest);
            HttpMethod httpMethod = HttpMethod.parseString(httpRequest.getMethod());
            if (logger.isDebugEnabled()) {
                System.out.println(httpMethod + " => " + url);
            }
            HttpUriRequest proxiedRequest = createHttpUriRequest(httpMethod, url);

            // headers
            for (Header header : httpRequest.getHeaders()) {
                String headerName = header.getName();
                if (!headerName.equalsIgnoreCase(HTTP.CONTENT_LEN) && !headerName.equalsIgnoreCase(HTTP.TRANSFER_ENCODING)) {
                    for (String headerValue : header.getValues()) {
                        proxiedRequest.addHeader(headerName, headerValue);
                    }
                }
            }

            // cookies
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

            // body
            String body = httpRequest.getBody() != null ? httpRequest.getBody().toString() : "";
            if (proxiedRequest.containsHeader(HTTP.CONTENT_LEN)) {
                proxiedRequest.setHeader(HTTP.CONTENT_LEN, "" + body.length());
            }
            if (proxiedRequest instanceof HttpEntityEnclosingRequest) {
                ((HttpEntityEnclosingRequest) proxiedRequest).setEntity(new StringEntity(body));
            }

            // logging
            if (logger.isTraceEnabled()) {
                logger.trace("Proxy sending request:\n" + new ObjectMapper()
                        .setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT)
                        .setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL)
                        .setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY)
                        .configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false)
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(httpRequest));
            }

            return apacheHttpClientToMockServerResponseMapper.mapApacheHttpClientResponseToMockServerResponse(this.httpClient.execute(proxiedRequest));
        } catch (IOException ioe) {
            if (ioe.getCause() instanceof CircularRedirectException) {
                logger.debug("Circular redirect aborting request", ioe);
                return new HttpResponse();
            } else {
                throw new RuntimeException("IOException while sending request for url [" + httpRequest.getURL() + "]", ioe);
            }
        } catch (URISyntaxException urle) {
            throw new RuntimeException("URISyntaxException for url [" + httpRequest.getURL() + "]", urle);
        }
    }

    private URI buildUrl(HttpRequest httpRequest) throws URISyntaxException {
        URI url = new URI(URLEncoder.encodeURL(httpRequest.getURL()));
        if (url.getQuery() != null) {
            httpRequest.withQueryStringParameters(new QueryStringDecoder("?" + url.getQuery()).parameters());
        }
        StringBuilder queryString = new StringBuilder();
        List<Parameter> queryStringParameters = httpRequest.getQueryStringParameters();
        for (int i = 0; i < queryStringParameters.size(); i++) {
            Parameter parameter = queryStringParameters.get(i);
            if (parameter.getValues().isEmpty()) {
                queryString.append(parameter.getName());
                queryString.append('=');
            } else {
                List<String> values = parameter.getValues();
                for (int j = 0; j < values.size(); j++) {
                    String value = values.get(j);
                    queryString.append(parameter.getName());
                    queryString.append('=');
                    queryString.append(value);
                    if (j < (values.size() - 1)) {
                        queryString.append('&');
                    }
                }
            }
            if (i < (queryStringParameters.size() - 1)) {
                queryString.append('&');
            }
        }
        return new URI(url.getScheme(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), (queryString.toString().isEmpty() ? null : queryString.toString()), url.getFragment());
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
                logger.trace("Not match found for http method [" + string + "]");
            }
            return GET;
        }

    }
}
