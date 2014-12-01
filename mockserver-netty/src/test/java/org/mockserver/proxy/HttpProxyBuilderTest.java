package org.mockserver.proxy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.proxy.direct.DirectProxy;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

/**
 * @author jamesdbloom
 */
public class HttpProxyBuilderTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldConfigureProxy() {
        // given
        // - some ports
        Integer port = PortFactory.findFreePort();

        // when
        Proxy httpProxy = new ProxyBuilder()
                .withLocalPort(port)
                .build();

        try {
            // then
            assertThat(httpProxy, is(instanceOf(HttpProxy.class)));
            HttpProxy unificationProxy = (HttpProxy)httpProxy;
            assertThat(unificationProxy.getPort(), is(port));
        } finally {
            httpProxy.stop();
        }
    }

    @Test
    public void shouldConfigureDirectProxy() {
        // given
        // - some ports
        Integer port = PortFactory.findFreePort();
        String directRemoteHost = "random.host";
        Integer directRemotePort = PortFactory.findFreePort();

        // when
        Proxy httpProxy = new ProxyBuilder()
                .withLocalPort(port)
                .withDirect(directRemoteHost, directRemotePort)
                .build();

        try {
            // then
            assertThat(httpProxy, is(instanceOf(DirectProxy.class)));
            DirectProxy directProxy = (DirectProxy)httpProxy;
            assertThat(directProxy.getLocalPort(), is(port));
            assertThat(directProxy.getRemoteHost(), is(directRemoteHost));
            assertThat(directProxy.getRemotePort(), is(directRemotePort));
        } finally {
            httpProxy.stop();
        }
    }


    @Test
    public void shouldThrowExceptionWhenNoLocalPort() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("LocalPort must be specified before the proxy is started"));

        // when
        new ProxyBuilder().build();
    }
}
