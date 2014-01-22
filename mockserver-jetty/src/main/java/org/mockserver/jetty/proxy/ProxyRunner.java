package org.mockserver.jetty.proxy;

import com.google.common.annotations.VisibleForTesting;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.mockserver.model.HttpRequest;
import org.mockserver.proxy.ProxyServlet;
import org.mockserver.proxy.filters.ProxyRequestFilter;
import org.mockserver.proxy.filters.ProxyResponseFilter;
import org.mockserver.proxy.socks.SocksProxy;
import org.mockserver.jetty.runner.AbstractRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.List;

import static org.mockserver.configuration.SystemProperties.proxyStopPort;
import static org.mockserver.configuration.SystemProperties.socksPort;

/**
 * @author jamesdbloom
 */
public class ProxyRunner extends AbstractRunner<ProxyRunner> {

    private static Logger logger = LoggerFactory.getLogger(ProxyRunner.class);
    @VisibleForTesting
    ProxyServlet proxyServlet = new ProxyServlet();

    public static ProxySelector proxySelector() {
        if (Boolean.parseBoolean(System.getProperty("proxySet"))) {
            return new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return Arrays.asList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")))));
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
                }
            };
        } else {
            throw new IllegalStateException("ProxySelector can not be returned proxy has not been started yet");
        }
    }

    protected HttpServlet getServlet() {
        return proxyServlet;
    }

    @Override
    protected int stopPort(Integer port, Integer securePort) {
        return proxyStopPort(port, securePort);
    }

    @Override
    protected void extendHTTPConfig(HttpConfiguration https_config) {
        https_config.addCustomizer(new ForwardedRequestCustomizer());
    }

    @Override
    protected void serverStarted(final Integer port, final Integer securePort) {
        System.setProperty("proxySet", "true");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("java.net.useSystemProxies", "true");

        int socksPort = socksPort();
        if (socksPort != -1) {
            new SocksProxy(socksPort, new InetSocketAddress("127.0.0.1", port), new InetSocketAddress("127.0.0.1", securePort));
            System.setProperty("http.proxyPort", "" + socksPort);
            java.net.ProxySelector.setDefault(proxySelector());
        } else {
            System.setProperty("http.proxyPort", port.toString());
        }
    }

    protected void serverStopped() {
        System.clearProperty("proxySet");
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("java.net.useSystemProxies");
    }

    /**
     * Add filter for HTTP requests, each filter get called before each request is proxied, if the filter return null then the request is not proxied
     *
     * @param httpRequest the request to match against for this filter
     * @param filter the filter to execute for this request, if the filter returns null the request will not be proxied
     */
    public ProxyRunner withFilter(HttpRequest httpRequest, ProxyRequestFilter filter) {
        proxyServlet.withFilter(httpRequest, filter);
        return this;
    }

    /**
     * Add filter for HTTP response, each filter get called after each request has been proxied
     *
     * @param httpRequest the request to match against for this filter
     * @param filter the filter that is executed after this request has been proxied
     */
    public ProxyRunner withFilter(HttpRequest httpRequest, ProxyResponseFilter filter) {
        proxyServlet.withFilter(httpRequest, filter);
        return this;
    }
}
