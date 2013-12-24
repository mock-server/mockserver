package org.mockserver.proxy;

import org.eclipse.jetty.http.HttpHeader;
import org.mockserver.client.http.HttpRequestClient;
import org.mockserver.mappers.HttpServletRequestMapper;
import org.mockserver.mappers.HttpServletResponseMapper;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.filters.ProxyRequestFilter;
import org.mockserver.proxy.filters.ProxyResponseFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jamesdbloom
 */
public class ProxyServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final HttpServletRequestMapper httpServletRequestMapper = new HttpServletRequestMapper();
    private final HttpServletResponseMapper httpServletResponseMapper = new HttpServletResponseMapper();
    private HttpRequestClient httpRequestClient;
    private Map<HttpRequest, List<ProxyResponseFilter>> responseFilters = new ConcurrentHashMap<>();
    private Map<HttpRequest, List<ProxyRequestFilter>> requestFilters = new ConcurrentHashMap<>();

    public ProxyServlet() {

        httpRequestClient = new HttpRequestClient("");
    }

    public ProxyServlet withFilter(HttpRequest httpRequest, ProxyRequestFilter filter) {
        if (requestFilters.containsKey(httpRequest)) {
            requestFilters.get(httpRequest).add(filter);
        } else {
            List<ProxyRequestFilter> filterList = Collections.synchronizedList(new ArrayList<ProxyRequestFilter>());
            filterList.add(filter);
            requestFilters.put(httpRequest, filterList);
        }
        return this;
    }

    public ProxyServlet withFilter(HttpRequest httpRequest, ProxyResponseFilter filter) {
        if (responseFilters.containsKey(httpRequest)) {
            responseFilters.get(httpRequest).add(filter);
        } else {
            List<ProxyResponseFilter> filterList = Collections.synchronizedList(new ArrayList<ProxyResponseFilter>());
            filterList.add(filter);
            responseFilters.put(httpRequest, filterList);
        }
        return this;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doTrace(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response) {
        HttpRequest httpRequest = httpServletRequestMapper.createHttpRequest(request);
        HttpRequestMatcher httpRequestMatcher = MatcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest filterRequest : requestFilters.keySet()) {
            if (httpRequestMatcher.matches(httpRequest)) {
                for (ProxyRequestFilter proxyRequestFilter : requestFilters.get(filterRequest)) {
                    proxyRequestFilter.onRequest(httpRequest);
                }
            }
        }
        sendRequest(httpRequestMatcher, httpRequest, response);
    }

    private void sendRequest(HttpRequestMatcher httpRequestMatcher, final HttpRequest httpRequest, final HttpServletResponse httpServletResponse) {
        // TODO add logic to stream response (chunk) back to client if no matching filter, this will allow for proxying larger payloads
        List<String> headersToRemove = Arrays.asList(
                "proxy-connection",
                "connection",
                "keep-alive",
                "transfer-encoding",
                "te",
                "trailer",
                "proxy-authorization",
                "proxy-authenticate",
                "upgrade"
        );
        List<Header> filteredHeaders = new ArrayList<>();
        for (Header header : httpRequest.getHeaders()) {
            if (!headersToRemove.contains(header.getName().toLowerCase(Locale.ENGLISH))) {
                filteredHeaders.add(header);
            }
        }

        httpRequest.withHeaders(filteredHeaders);

        HttpResponse httpResponse = httpRequestClient.sendRequest(httpRequest);

        for (HttpRequest filterRequest : responseFilters.keySet()) {
            if (httpRequestMatcher.matches(httpRequest)) {
                for (ProxyResponseFilter proxyFilter : responseFilters.get(filterRequest)) {
                    proxyFilter.onResponse(httpRequest, httpResponse);
                }
            }
        }

        httpServletResponseMapper.mapHttpServletResponse(httpResponse, httpServletResponse);
    }

//    private void sendRequest(final HttpRequest httpRequest, final int maxTimeout, final HttpServletResponse httpServletResponse) {
//        final SettableFuture<Response> responseFuture = SettableFuture.create();
//        final ByteBuffer contentBuffer = ByteBuffer.allocate(1024 * 500);
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    if (logger.isTraceEnabled()) {
//                        logger.trace("Proxy received request:\n" + new HttpRequestSerializer().serialize(httpRequest));
//                    }
//                    String url = httpRequest.getURL();
//                    if (Strings.isNullOrEmpty(url)) {
//                        url = baseUri + httpRequest.getPath() + (Strings.isNullOrEmpty(httpRequest.getQueryString()) ? "" : '?' + httpRequest.getQueryString());
//                    }
//
//                    Request request = httpClient
//                            .newRequest(url)
//                            .method(HttpMethod.fromString(httpRequest.getMethod()));
//                    request.content(new StringContentProvider(httpRequest.getBody()));
//                    for (Header header : httpRequest.getHeaders()) {
//                        for (String value : header.getValues()) {
//                            request.header(header.getName(), value);
//                        }
//                    }
//                    if (HttpMethod.fromString(httpRequest.getMethod()) == HttpMethod.POST
//                            && !request.getHeaders().containsKey(HttpHeader.CONTENT_TYPE.asString())) {
//                        // handle missing header which causes error with IIS
//                        request.header(HttpHeader.CONTENT_TYPE, MimeTypes.Type.FORM_ENCODED.asString());
//                    }
//                    StringBuilder stringBuilder = new StringBuilder();
//                    for (Cookie cookie : httpRequest.getCookies()) {
//                        for (String value : cookie.getValues()) {
//                            stringBuilder.append(cookie.getName()).append("=").append(value).append("; ");
//                        }
//                    }
//                    if (stringBuilder.length() > 0) {
//                        request.header("Cookie", stringBuilder.toString());
//                    }
//                    if (logger.isTraceEnabled()) {
//                        logger.trace("Proxy sending request:\n" + new ObjectMapper()
//                                .setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT)
//                                .setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL)
//                                .setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY)
//                                .configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false)
//                                .writerWithDefaultPrettyPrinter()
//                                .writeValueAsString(request));
//                    }
//                    request
//                            .onResponseContent(new Response.ContentListener() {
//                                @Override
//                                public void onContent(Response response, ByteBuffer chunk) {
//                                    contentBuffer.put(chunk);
//                                }
//                            })
//                            .send(new Response.CompleteListener() {
//                                @Override
//                                public void onComplete(Result result) {
//                                    if (result.isFailed()) {
//                                        responseFuture.setException(result.getFailure());
//                                    } else {
//                                        responseFuture.set(result.getResponse());
//                                    }
//                                }
//                            });
//                } catch (Exception e) {
//                    responseFuture.setException(e);
//                }
//            }
//        }).start();
//        try {
//            Response proxiedResponse = responseFuture.get(maxTimeout, TimeUnit.SECONDS);
//            byte[] content = new byte[contentBuffer.position()];
//            contentBuffer.flip();
//            contentBuffer.get(content);
//            httpServletResponseMapper.mapHttpServletResponse(httpClientResponseMapper.buildHttpResponse(proxiedResponse, content), httpServletResponse);
//        } catch (Exception e) {
//            throw new RuntimeException("Exception sending request to [" + httpRequest.getURL() + "] with body [" + httpRequest.getBody() + "]", e);
//        }
//    }
}
