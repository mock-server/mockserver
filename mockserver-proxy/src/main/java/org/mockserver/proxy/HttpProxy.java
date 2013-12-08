package org.mockserver.proxy;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.DefaultHttpProxyServer;
import org.littleshoot.proxy.HttpFilter;
import org.littleshoot.proxy.HttpRequestFilter;
import org.littleshoot.proxy.HttpResponseFilters;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author bloomj
 */
public class HttpProxy {

    private final Map<String, String> headersToAdd = new ConcurrentHashMap<String, String>();
    private final Map<String, Iterable> headersToOverride = new ConcurrentHashMap<String, Iterable>();
    private final RequestResponseLogger requestResponseLogger = new RequestResponseLogger();

    public static final String COOKIE_HEADER_NAME = "Cookie";

    private int port;

    private DefaultHttpProxyServer defaultHttpProxyServer;

    public void overrideHeader(String name, String... values) {
        headersToOverride.put(name, Arrays.asList(values));
    }

    public void addHeader(String name, String value) {
        headersToAdd.put(name, value);
    }

    public void log(String urlRegex) {
        requestResponseLogger.log(urlRegex);
    }

    public void stopLogging(String urlRegex) {
        requestResponseLogger.stopLogging(urlRegex);
    }

    public List<HttpRequest> clearLog(String urlRegex) {
        return requestResponseLogger.clearLog(urlRegex);
    }

    public List<HttpRequest> getRequests(String urlRegex, Map<String, List<String>> queryParameters) {
        return requestResponseLogger.getRequests(urlRegex, queryParameters);
    }

    public int startProxy() {
        port = findFreePort();

        HttpRequestFilter requestFilter = new HttpRequestFilter() {
            @Override
            public void filter(HttpRequest httpRequest) {
                addHeaders(httpRequest);
                overrideHeaders(httpRequest);
                requestResponseLogger.logRequestAndResponse(httpRequest);
            }
        };

        defaultHttpProxyServer = new DefaultHttpProxyServer(port, requestFilter, new HttpResponseFilters() {
            @Override
            public HttpFilter getFilter(String hostAndPort) {
                return null;
            }
        });
        Thread proxyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                defaultHttpProxyServer.start();
            }
        });
        proxyThread.setDaemon(true);
        proxyThread.start();
        return port;
    }

    public int getPort() {
        return port;
    }

    private void addHeaders(HttpRequest httpRequest) {
        for (String headerName : headersToAdd.keySet()) {
            if (headerName.equals(COOKIE_HEADER_NAME) && httpRequest.getHeader(headerName) != null) {
                appendCookieValue(httpRequest, headerName);
            } else {
                httpRequest.addHeader(headerName, headersToAdd.get(headerName));
            }
        }
    }

    private void appendCookieValue(HttpRequest httpRequest, String headerName) {
        String currentValue = httpRequest.getHeader(headerName);
        if (currentValue.trim().endsWith(";")) {
            currentValue += (" " + headersToAdd.get(headerName));
        } else {
            currentValue += ("; " + headersToAdd.get(headerName));
        }
        httpRequest.setHeader(headerName, currentValue);
    }

    private void overrideHeaders(HttpRequest httpRequest) {
        for (String headerName : headersToOverride.keySet()) {
            httpRequest.setHeader(headerName, headersToOverride.get(headerName));
        }
    }

    public void stopProxy() {
        if (defaultHttpProxyServer != null) {
            defaultHttpProxyServer.stop();
        }
    }

    public static int findFreePort() {
        int port = -1;
        try {
            ServerSocket server = new ServerSocket(0);
            port = server.getLocalPort();
            server.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new RuntimeException(ioe);
        }
        return port;
    }

    @Override
    public String toString() {
        return requestResponseLogger.toString();
    }
}
