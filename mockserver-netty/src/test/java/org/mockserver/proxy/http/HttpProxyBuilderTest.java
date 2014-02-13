package org.mockserver.proxy.http;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author jamesdbloom
 */
public class HttpProxyBuilderTest {

    @Test
    public void shouldConfigureHTTPPort() {
        // given
        // - some ports
        Integer port = new Random().nextInt();
        Integer securePort = null;
        Integer socksPort = null;
        Integer directLocalPort = null;
        Integer directLocalSecurePort = null;
        String directRemoteHost = null;
        Integer directRemotePort = null;
        // - a build and http proxy
        HttpProxyBuilder httpProxyBuilder = spy(new HttpProxyBuilder());
        HttpProxy httpProxy = mock(HttpProxy.class);
        when(httpProxyBuilder.newHttpProxy()).thenReturn(httpProxy);

        // when
        HttpProxy actual = httpProxyBuilder.withHTTPPort(port).build();

        // then
        assertEquals(httpProxy, actual);
        verify(httpProxy).start(
                port,
                securePort,
                socksPort,
                directLocalPort,
                directLocalSecurePort,
                directRemoteHost,
                directRemotePort
        );
    }

    @Test
    public void shouldConfigureHTTPSPort() {
        // given
        // - some ports
        Integer port = null;
        Integer securePort = new Random().nextInt();
        Integer socksPort = null;
        Integer directLocalPort = null;
        Integer directLocalSecurePort = null;
        String directRemoteHost = null;
        Integer directRemotePort = null;
        // - a build and http proxy
        HttpProxyBuilder httpProxyBuilder = spy(new HttpProxyBuilder());
        HttpProxy httpProxy = mock(HttpProxy.class);
        when(httpProxyBuilder.newHttpProxy()).thenReturn(httpProxy);

        // when
        HttpProxy actual = httpProxyBuilder.withHTTPSPort(securePort).build();

        // then
        assertEquals(httpProxy, actual);
        verify(httpProxy).start(
                port,
                securePort,
                socksPort,
                directLocalPort,
                directLocalSecurePort,
                directRemoteHost,
                directRemotePort
        );
    }

    @Test
    public void shouldConfigureSOCKSPort() {
        // given
        // - some ports
        Integer port = null;
        Integer securePort = null;
        Integer socksPort = new Random().nextInt();
        Integer directLocalPort = null;
        Integer directLocalSecurePort = null;
        String directRemoteHost = null;
        Integer directRemotePort = null;
        // - a build and http proxy
        HttpProxyBuilder httpProxyBuilder = spy(new HttpProxyBuilder());
        HttpProxy httpProxy = mock(HttpProxy.class);
        when(httpProxyBuilder.newHttpProxy()).thenReturn(httpProxy);

        // when
        HttpProxy actual = httpProxyBuilder.withSOCKSPort(socksPort).build();

        // then
        assertEquals(httpProxy, actual);
        verify(httpProxy).start(
                port,
                securePort,
                socksPort,
                directLocalPort,
                directLocalSecurePort,
                directRemoteHost,
                directRemotePort
        );
    }

    @Test
    public void shouldConfigureDirectProxy() {
        // given
        // - some ports
        Integer port = null;
        Integer securePort = null;
        Integer socksPort = null;
        Integer directLocalPort = new Random().nextInt();
        Integer directLocalSecurePort = null;
        String directRemoteHost = "random.host";
        Integer directRemotePort = new Random().nextInt();
        // - a build and http proxy
        HttpProxyBuilder httpProxyBuilder = spy(new HttpProxyBuilder());
        HttpProxy httpProxy = mock(HttpProxy.class);
        when(httpProxyBuilder.newHttpProxy()).thenReturn(httpProxy);

        // when
        HttpProxy actual = httpProxyBuilder.withDirect(directLocalPort, directRemoteHost, directRemotePort).build();

        // then
        assertEquals(httpProxy, actual);
        verify(httpProxy).start(
                port,
                securePort,
                socksPort,
                directLocalPort,
                directLocalSecurePort,
                directRemoteHost,
                directRemotePort
        );
    }

    @Test
    public void shouldConfigureDirectSSLProxy() {
        // given
        // - some ports
        Integer port = null;
        Integer securePort = null;
        Integer socksPort = null;
        Integer directLocalPort = null;
        Integer directLocalSecurePort = new Random().nextInt();
        String directRemoteHost = "random.host";
        Integer directRemotePort = new Random().nextInt();
        // - a build and http proxy
        HttpProxyBuilder httpProxyBuilder = spy(new HttpProxyBuilder());
        HttpProxy httpProxy = mock(HttpProxy.class);
        when(httpProxyBuilder.newHttpProxy()).thenReturn(httpProxy);

        // when
        HttpProxy actual = httpProxyBuilder.withDirectSSL(directLocalSecurePort, directRemoteHost, directRemotePort).build();

        // then
        assertEquals(httpProxy, actual);
        verify(httpProxy).start(
                port,
                securePort,
                socksPort,
                directLocalPort,
                directLocalSecurePort,
                directRemoteHost,
                directRemotePort
        );
    }

    @Test
    public void shouldReturnCorrectObject() {
        HttpProxy httpProxy = new HttpProxyBuilder().build();
        assertTrue(httpProxy instanceof HttpProxy);
        httpProxy.stop();
        Thread thread = new HttpProxyBuilder().buildAndReturnThread();
        assertTrue(thread instanceof Thread);
        thread.stop();
    }
}
