package org.mockserver.cli;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.StandardOutputStreamLog;
import org.mockito.Mock;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;

import java.io.PrintStream;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MainTest {

    private final static Integer SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static String WHITESPACE_IN_OUTPUT = "\n                         ";
    @Mock
    private ProxyBuilder mockProxyBuilder;
    @Mock
    private MockServerBuilder mockMockServerBuilder;
    @Rule
    public final StandardOutputStreamLog log = new StandardOutputStreamLog();

    @Before
    public void setupMocks() {
        initMocks(this);
        Main.mockServerBuilder = mockMockServerBuilder;
        Main.httpProxyBuilder = mockProxyBuilder;
        Main.shutdownOnUsage = false;

        when(mockMockServerBuilder.withHTTPPort(anyInt())).thenReturn(mockMockServerBuilder);
        when(mockProxyBuilder.withLocalPort(anyInt())).thenReturn(mockProxyBuilder);
    }

    @Test
    public void shouldParseArgumentsForAllPortsAndNoSecureFlag() {
        Main.main("-serverPort", SERVER_HTTP_PORT.toString(), "-proxyPort", PROXY_HTTP_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_HTTP_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_HTTP_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForAllPortsInReverseOrder() {
        Main.main("-proxyPort", PROXY_HTTP_PORT.toString(), "-serverPort", SERVER_HTTP_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_HTTP_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_HTTP_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForServerOnly() {
        Main.main("-serverPort", SERVER_HTTP_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_HTTP_PORT);
        verify(mockMockServerBuilder).build();
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldParseArgumentsForProxyOnly() {
        Main.main("-proxyPort", PROXY_HTTP_PORT.toString());

        verifyZeroInteractions(mockMockServerBuilder);
        verify(mockProxyBuilder).withLocalPort(PROXY_HTTP_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldPrintOutUsageForInvalidPort() {
        Main.main("-proxyPort", "1", "-invalidOption", "2");

        assert log.getLog().contains("specifies the HTTP and HTTPS port for the"
                + WHITESPACE_IN_OUTPUT
                + "MockServer port unification is used to support"
                + WHITESPACE_IN_OUTPUT
                + "HTTP and HTTPS on the same port");
    }

    @Test
    public void shouldPrintOutUsageForMissingLastPort() {
        Main.main("-serverPort", "1", "-proxyPort");

        assert log.getLog().contains("specifies the HTTP, HTTPS, SOCKS and HTTP CONNECT"
            + WHITESPACE_IN_OUTPUT
            + "port for proxy, port unification supports for all"
            + WHITESPACE_IN_OUTPUT
            + "protocols on the same port");
    }

    @Test
    public void shouldPrintOutUsageForMissingFirstPort() {
        Main.main("-serverPort", "-proxyPort", "2");

        assert log.getLog().contains("specifies the HTTP, HTTPS, SOCKS and HTTP CONNECT"
                + WHITESPACE_IN_OUTPUT
                + "port for proxy, port unification supports for all"
                + WHITESPACE_IN_OUTPUT
                + "protocols on the same port");
    }

    @Test
    public void shouldPrintOutUsageForNoArguments() {
        // using non static reference and constructor for coverage
        new Main().main();

        assert log.getLog().contains("specifies the HTTP, HTTPS, SOCKS and HTTP CONNECT"
                + WHITESPACE_IN_OUTPUT
                + "port for proxy, port unification supports for all"
                + WHITESPACE_IN_OUTPUT
                + "protocols on the same port");
        verifyZeroInteractions(mockMockServerBuilder);
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldPrintOutUsageForJksPathAndNoPassword() {
        Main.main("-proxyPort", "2", "-jksPath", "/dev/null");

        assert log.getLog().contains("specifies the HTTP, HTTPS, SOCKS and HTTP CONNECT"
                + WHITESPACE_IN_OUTPUT
                + "port for proxy, port unification supports for all"
                + WHITESPACE_IN_OUTPUT
                + "protocols on the same port");
    }

    @Test
    public void shouldPrintOutUsageForPasswordAndNoJksPath() {
        Main.main("-proxyPort", "2", "-keyPassword", "password");

        assert log.getLog().contains("specifies the HTTP, HTTPS, SOCKS and HTTP CONNECT"
                + WHITESPACE_IN_OUTPUT
                + "port for proxy, port unification supports for all"
                + WHITESPACE_IN_OUTPUT
                + "protocols on the same port");
    }

    @Test
    public void shouldPrintOutUsageForInvalidJksPath() {
        Main.main("-proxyPort", "2", "-jksPath", "/tmp/thisFileDoesNotExist");

        assert log.getLog().contains("specifies the HTTP, HTTPS, SOCKS and HTTP CONNECT"
                + WHITESPACE_IN_OUTPUT
                + "port for proxy, port unification supports for all"
                + WHITESPACE_IN_OUTPUT
                + "protocols on the same port");
    }
}
