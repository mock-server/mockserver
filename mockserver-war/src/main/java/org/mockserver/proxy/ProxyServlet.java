package org.mockserver.proxy;

import org.mockserver.client.http.HttpRequestClient;
import org.mockserver.mappers.HttpClientResponseMapper;
import org.mockserver.mappers.HttpServletRequestMapper;
import org.mockserver.mappers.HttpServletResponseMapper;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.filters.ProxyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jamesdbloom
 */
public class ProxyServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final int maxTimeout;
    private final HttpServletRequestMapper httpServletRequestMapper = new HttpServletRequestMapper();
    private final HttpServletResponseMapper httpServletResponseMapper = new HttpServletResponseMapper();
    private final HttpClientResponseMapper httpClientResponseMapper = new HttpClientResponseMapper();
    private HttpRequestClient httpRequestClient;
    private Map<HttpRequest, List<ProxyFilter>> filters = new ConcurrentHashMap<>();

    public ProxyServlet() {
        try {
            maxTimeout = Integer.parseInt(System.getProperty("proxy.maxTimeout", "60"));
        } catch (NumberFormatException nfe) {
            logger.error("NumberFormatException converting proxy.maxTimeout with value [" + System.getProperty("proxy.maxTimeout") + "]", nfe);
            throw new RuntimeException("NumberFormatException converting proxy.maxTimeout with value [" + System.getProperty("proxy.maxTimeout") + "]", nfe);
        }
        httpRequestClient = new HttpRequestClient("");
    }

    public ProxyServlet withFilter(HttpRequest httpRequest, ProxyFilter filter) {
        if (filters.containsKey(httpRequest)) {
            filters.get(httpRequest).add(filter);
        } else {
            List<ProxyFilter> filterList = Collections.synchronizedList(new ArrayList<ProxyFilter>());
            filterList.add(filter);
            filters.put(httpRequest, filterList);
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
        for (HttpRequest filterRequest : filters.keySet()) {
            if (httpRequestMatcher.matches(httpRequest)) {
                for (ProxyFilter proxyFilter : filters.get(filterRequest)) {
                    proxyFilter.onRequest(httpRequest);
                }
            }
        }
        sendRequest(httpRequestMatcher, httpRequest, response);
    }

    private void sendRequest(HttpRequestMatcher httpRequestMatcher, final HttpRequest httpRequest, final HttpServletResponse httpServletResponse) {
        System.out.println(httpRequest.getMethod() + "=>" + httpRequest.getURL());
        HttpResponse httpResponse = httpRequestClient.sendRequest(httpRequest, maxTimeout);

        for (HttpRequest filterRequest : filters.keySet()) {
            if (httpRequestMatcher.matches(httpRequest)) {
                for (ProxyFilter proxyFilter : filters.get(filterRequest)) {
                    proxyFilter.onResponse(httpRequest, httpResponse);
                }
            }
        }

        httpServletResponseMapper.mapHttpServletResponse(httpResponse, httpServletResponse);
    }
}
