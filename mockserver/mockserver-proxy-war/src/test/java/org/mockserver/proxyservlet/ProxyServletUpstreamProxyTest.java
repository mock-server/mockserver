package org.mockserver.proxyservlet;

import org.junit.Test;
import org.mockserver.echo.http.EchoServer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.InetSocketAddress;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * A forward action goes through the configured {@code forwardHttpProxy}, as it does on the Netty server.
 */
public class ProxyServletUpstreamProxyTest {

    @Test
    public void shouldForwardThroughConfiguredHttpProxy() throws Exception {
        EchoServer proxy = new EchoServer(false);
        ProxyServlet servlet = new ProxyServlet(configuration().forwardHttpProxy(new InetSocketAddress("127.0.0.1", proxy.getPort())));
        try {
            MockHttpServletResponse created = new MockHttpServletResponse();
            // .invalid never resolves, so only the proxy can answer
            servlet.service(request("PUT", "/mockserver/expectation",
                "{ \"httpRequest\": { \"path\": \"/via-proxy\" }, \"httpForward\": { \"host\": \"upstream.invalid\", \"port\": 80 } }"), created);
            assertThat(created.getStatus(), is(201));

            MockHttpServletResponse response = new MockHttpServletResponse();
            servlet.service(request("POST", "/via-proxy", "through the proxy"), response);

            assertThat(response.getStatus(), is(200));
            assertThat(new String(response.getContentAsByteArray(), UTF_8), is("through the proxy"));
            assertThat(proxy.getLastRequest().getFirstHeader("Host"), is("upstream.invalid"));
        } finally {
            servlet.destroy();
            proxy.stop();
        }
    }

    private static MockHttpServletRequest request(String method, String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContent(body.getBytes(UTF_8));
        return request;
    }
}
