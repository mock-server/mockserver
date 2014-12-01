package org.mockserver.cli;

import org.junit.Before;
import org.junit.Test;
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
    private final static Integer SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_HTTPS_PORT = PortFactory.findFreePort();

    @Mock
    private ProxyBuilder mockProxyBuilder;
    @Mock
    private MockServerBuilder mockMockServerBuilder;
    @Mock
    private PrintStream mockPrintStream;

    @Before
    public void setupMocks() {
        initMocks(this);
        Main.mockServerBuilder = mockMockServerBuilder;
        Main.httpProxyBuilder = mockProxyBuilder;
        Main.outputPrintStream = mockPrintStream;
        Main.shutdownOnUsage = false;

        when(mockMockServerBuilder.withHTTPPort(anyInt())).thenReturn(mockMockServerBuilder);
        when(mockMockServerBuilder.withHTTPSPort(anyInt())).thenReturn(mockMockServerBuilder);
        when(mockProxyBuilder.withLocalPort(anyInt())).thenReturn(mockProxyBuilder);
    }

    @Test
    public void shouldParseArgumentsForAllThreePortsAndNoSecureFlag() {
        Main.main("-serverPort", SERVER_HTTP_PORT.toString(), "-serverSecurePort", SERVER_HTTPS_PORT.toString(), "-proxyPort", PROXY_HTTP_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_HTTP_PORT);
        verify(mockMockServerBuilder).withHTTPSPort(SERVER_HTTPS_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_HTTP_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForAllFourPortsInReverseOrder() {
        Main.main("-proxyPort", PROXY_HTTP_PORT.toString(), "-serverSecurePort", SERVER_HTTPS_PORT.toString(), "-serverPort", SERVER_HTTP_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_HTTP_PORT);
        verify(mockMockServerBuilder).withHTTPSPort(SERVER_HTTPS_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_HTTP_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForProxyAndServerOnlyNonSecurePorts() {
        Main.main("-serverPort", SERVER_HTTP_PORT.toString(), "-proxyPort", PROXY_HTTP_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_HTTP_PORT);
        verify(mockMockServerBuilder).withHTTPSPort(null);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_HTTP_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForProxyAndServerOnlySecurePorts() {
        Main.main("-serverSecurePort", SERVER_HTTPS_PORT.toString(), "-proxyPort", PROXY_HTTP_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(null);
        verify(mockMockServerBuilder).withHTTPSPort(SERVER_HTTPS_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_HTTP_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForServerOnlyBothPorts() {
        Main.main("-serverPort", SERVER_HTTP_PORT.toString(), "-serverSecurePort", SERVER_HTTPS_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_HTTP_PORT);
        verify(mockMockServerBuilder).withHTTPSPort(SERVER_HTTPS_PORT);
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

        verify(mockPrintStream).println(Main.USAGE);
    }

    @Test
    public void shouldPrintOutUsageForMissingLastPort() {
        Main.main("-serverPort", "1", "-proxyPort");

        verify(mockPrintStream).println(Main.USAGE);
    }

    @Test
    public void shouldPrintOutUsageForMissingFirstPort() {
        Main.main("-serverPort", "-proxyPort", "2");

        verify(mockPrintStream).println(Main.USAGE);
    }

    @Test
    public void shouldPrintOutUsageForNoArguments() {
        // using non static reference and constructor for coverage
        new Main().main();

        verify(mockPrintStream).println(Main.USAGE);
        verifyZeroInteractions(mockMockServerBuilder);
        verifyZeroInteractions(mockProxyBuilder);
    }
}
