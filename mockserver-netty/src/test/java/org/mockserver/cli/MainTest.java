package org.mockserver.cli;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.mockserver.NettyMockServer;
import org.mockserver.proxy.http.HttpProxy;

import java.io.PrintStream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MainTest {

    @Mock
    private HttpProxy mockProxy;
    @Mock
    private NettyMockServer mockMockServer;
    @Mock
    private PrintStream mockPrintStream;

    @Before
    public void setupMocks() {
        initMocks(this);
        Main.proxy = mockProxy;
        Main.mockServer = mockMockServer;
        Main.outputPrintStream = mockPrintStream;
        Main.shutdownOnUsage = false;
    }

    @Test
    public void shouldParseArgumentsForAllFourPorts() {
        Main.main("-serverPort", "1", "-serverSecurePort", "2", "-proxyPort", "3", "-proxySecurePort", "4");

        verify(mockMockServer).start(1, 2);
        verify(mockProxy).startHttpProxy(3, 4);
    }

    @Test
    public void shouldParseArgumentsForAllFourPortsInReverseOrder() {
        Main.main("-proxySecurePort", "4", "-proxyPort", "3", "-serverSecurePort", "2", "-serverPort", "1");

        verify(mockMockServer).start(1, 2);
        verify(mockProxy).startHttpProxy(3, 4);
    }

    @Test
    public void shouldParseArgumentsForProxyAndServerOnlyNonSecurePorts() {
        Main.main("-serverPort", "1", "-proxyPort", "2");

        verify(mockMockServer).start(1, null);
        verify(mockProxy).startHttpProxy(2, null);
    }

    @Test
    public void shouldParseArgumentsForProxyAndServerOnlySecurePorts() {
        Main.main("-serverSecurePort", "1", "-proxySecurePort", "2");

        verify(mockMockServer).start(null, 1);
        verify(mockProxy).startHttpProxy(null, 2);
    }

    @Test
    public void shouldParseArgumentsForServerOnlyBothPorts() {
        Main.main("-serverPort", "1", "-serverSecurePort", "2");

        verify(mockMockServer).start(1, 2);
        verifyZeroInteractions(mockProxy);
    }

    @Test
    public void shouldParseArgumentsForProxyOnlyBothPorts() {
        Main.main("-proxyPort", "1", "-proxySecurePort", "2");

        verifyZeroInteractions(mockMockServer);
        verify(mockProxy).startHttpProxy(1, 2);
    }

    @Test
    public void shouldPrintOutUsageForInvalidPort() {
        Main.main("-proxyPort", "1", "-proxySecurePort", "a");

        verify(mockPrintStream).println(Main.USAGE);
    }

    @Test
    public void shouldPrintOutUsageForInvalidOption() {
        Main.main("-proxyPort", "1", "-invalidOption", "2");

        verify(mockPrintStream).println(Main.USAGE);
    }

    @Test
    public void shouldPrintOutUsageForMissingPort() {
        Main.main("-proxyPort", "1", "-proxySecurePort");

        verify(mockPrintStream).println(Main.USAGE);
    }

    @Test
    public void shouldPrintOutUsageForNoArguments() {
        // using non static reference and constructor for coverage
        new Main().main();

        verify(mockPrintStream).println(Main.USAGE);
        verifyZeroInteractions(mockMockServer);
        verifyZeroInteractions(mockProxy);
    }
}
