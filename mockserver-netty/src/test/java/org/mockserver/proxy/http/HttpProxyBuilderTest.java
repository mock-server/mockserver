package org.mockserver.proxy.http;

import org.junit.Test;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class HttpProxyBuilderTest {

    @Test
    public void shouldConfigureAllPortsProxy() {
        // given
        // - some ports
        Integer port = PortFactory.findFreePort();
        Integer securePort = PortFactory.findFreePort();
        Integer socksPort = PortFactory.findFreePort();
        Integer directLocalPort = PortFactory.findFreePort();
        Integer directLocalSecurePort = PortFactory.findFreePort();
        String directRemoteHost = "random.host";
        Integer directRemotePort = PortFactory.findFreePort();

        // when
        HttpProxy httpProxy = new HttpProxyBuilder()
                .withHTTPPort(port)
                .withHTTPSPort(securePort)
                .withSOCKSPort(socksPort)
                .withDirect(directLocalPort, directRemoteHost, directRemotePort)
                .withDirectSSL(directLocalSecurePort, directRemoteHost, directRemotePort)
                .build();

        try {
            // then
            assertThat(httpProxy.getPort(), is(port));
            assertThat(httpProxy.getSecurePort(), is(securePort));
            assertThat(httpProxy.getSocksPort(), is(socksPort));
            assertThat(httpProxy.getDirectLocalPort(), is(directLocalPort));
            assertThat(httpProxy.getDirectLocalSecurePort(), is(directLocalSecurePort));
            assertThat(httpProxy.getDirectRemoteHost(), is(directRemoteHost));
            assertThat(httpProxy.getDirectRemotePort(), is(directRemotePort));
        } finally {
            httpProxy.stop();
        }
    }

    @Test
    public void shouldConfigureHTTPPort() {
        // given
        // - some ports
        Integer port = PortFactory.findFreePort();
        Integer securePort = null;
        Integer socksPort = null;
        Integer directLocalPort = null;
        Integer directLocalSecurePort = null;
        String directRemoteHost = null;
        Integer directRemotePort = null;

        // when
        HttpProxy httpProxy = new HttpProxyBuilder()
                .withHTTPPort(port)
                .build();

        try {
            // then
            assertThat(httpProxy.getPort(), is(port));
            assertThat(httpProxy.getSecurePort(), is(securePort));
            assertThat(httpProxy.getSocksPort(), is(socksPort));
            assertThat(httpProxy.getDirectLocalPort(), is(directLocalPort));
            assertThat(httpProxy.getDirectLocalSecurePort(), is(directLocalSecurePort));
            assertThat(httpProxy.getDirectRemoteHost(), is(directRemoteHost));
            assertThat(httpProxy.getDirectRemotePort(), is(directRemotePort));
        } finally {
            httpProxy.stop();
        }
    }

    @Test
    public void shouldConfigureHTTPSPort() {
        // given
        // - some ports
        Integer port = null;
        Integer securePort = PortFactory.findFreePort();
        Integer socksPort = null;
        Integer directLocalPort = null;
        Integer directLocalSecurePort = null;
        String directRemoteHost = null;
        Integer directRemotePort = null;

        // when
        HttpProxy httpProxy = new HttpProxyBuilder()
                .withHTTPSPort(securePort)
                .build();

        try {
            // then
            assertThat(httpProxy.getPort(), is(port));
            assertThat(httpProxy.getSecurePort(), is(securePort));
            assertThat(httpProxy.getSocksPort(), is(socksPort));
            assertThat(httpProxy.getDirectLocalPort(), is(directLocalPort));
            assertThat(httpProxy.getDirectLocalSecurePort(), is(directLocalSecurePort));
            assertThat(httpProxy.getDirectRemoteHost(), is(directRemoteHost));
            assertThat(httpProxy.getDirectRemotePort(), is(directRemotePort));
        } finally {
            httpProxy.stop();
        }
    }

    @Test
    public void shouldConfigureSOCKSPort() {
        // given
        // - some ports
        Integer port = null;
        Integer securePort = null;
        Integer socksPort = PortFactory.findFreePort();
        Integer directLocalPort = null;
        Integer directLocalSecurePort = null;
        String directRemoteHost = null;
        Integer directRemotePort = null;

        // when
        HttpProxy httpProxy = new HttpProxyBuilder()
                .withSOCKSPort(socksPort)
                .build();

        try {
            // then
            assertThat(httpProxy.getPort(), is(port));
            assertThat(httpProxy.getSecurePort(), is(securePort));
            assertThat(httpProxy.getSocksPort(), is(socksPort));
            assertThat(httpProxy.getDirectLocalPort(), is(directLocalPort));
            assertThat(httpProxy.getDirectLocalSecurePort(), is(directLocalSecurePort));
            assertThat(httpProxy.getDirectRemoteHost(), is(directRemoteHost));
            assertThat(httpProxy.getDirectRemotePort(), is(directRemotePort));
        } finally {
            httpProxy.stop();
        }
    }

    @Test
    public void shouldConfigureDirectProxy() {
        // given
        // - some ports
        Integer port = null;
        Integer securePort = null;
        Integer socksPort = null;
        Integer directLocalPort = PortFactory.findFreePort();
        Integer directLocalSecurePort = null;
        String directRemoteHost = "random.host";
        Integer directRemotePort = PortFactory.findFreePort();

        // when
        HttpProxy httpProxy = new HttpProxyBuilder()
                .withDirect(directLocalPort, directRemoteHost, directRemotePort)
                .build();

        try {
            // then
            assertThat(httpProxy.getPort(), is(port));
            assertThat(httpProxy.getSecurePort(), is(securePort));
            assertThat(httpProxy.getSocksPort(), is(socksPort));
            assertThat(httpProxy.getDirectLocalPort(), is(directLocalPort));
            assertThat(httpProxy.getDirectLocalSecurePort(), is(directLocalSecurePort));
            assertThat(httpProxy.getDirectRemoteHost(), is(directRemoteHost));
            assertThat(httpProxy.getDirectRemotePort(), is(directRemotePort));
        } finally {
            httpProxy.stop();
        }
    }

    @Test
    public void shouldConfigureDirectSSLProxy() {
        // given
        // - some ports
        Integer port = null;
        Integer securePort = null;
        Integer socksPort = null;
        Integer directLocalPort = null;
        Integer directLocalSecurePort = PortFactory.findFreePort();
        String directRemoteHost = "random.host";
        Integer directRemotePort = PortFactory.findFreePort();

        // when
        HttpProxy httpProxy = new HttpProxyBuilder()
                .withDirectSSL(directLocalSecurePort, directRemoteHost, directRemotePort)
                .build();

        try {
            // then
            assertThat(httpProxy.getPort(), is(port));
            assertThat(httpProxy.getSecurePort(), is(securePort));
            assertThat(httpProxy.getSocksPort(), is(socksPort));
            assertThat(httpProxy.getDirectLocalPort(), is(directLocalPort));
            assertThat(httpProxy.getDirectLocalSecurePort(), is(directLocalSecurePort));
            assertThat(httpProxy.getDirectRemoteHost(), is(directRemoteHost));
            assertThat(httpProxy.getDirectRemotePort(), is(directRemotePort));
        } finally {
            httpProxy.stop();
        }
    }
}
