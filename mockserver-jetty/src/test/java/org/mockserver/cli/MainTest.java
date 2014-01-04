package org.mockserver.cli;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.proxy.ProxyRunner;
import org.mockserver.server.MockServerRunner;

import java.io.PrintStream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MainTest {

    @Mock
    private ProxyRunner mockProxyRunner;
    @Mock
    private MockServerRunner mockMockServerRunner;
    @Mock
    private PrintStream mockPrintStream;

    @Before
    public void setupMocks() {
        initMocks(this);
        Main.proxyRunner = mockProxyRunner;
        Main.mockServerRunner = mockMockServerRunner;
        Main.outputPrintStream = mockPrintStream;
        Main.shutdownOnUsage = false;
    }

    @Test
    public void shouldParseArgumentsForAllFourPorts() {
        Main.main("-serverPort", "1", "-serverSecurePort", "2", "-proxyPort", "3", "-proxySecurePort", "4");

        verify(mockMockServerRunner).start(1, 2);
        verify(mockProxyRunner).start(3, 4);
    }

    @Test
    public void shouldParseArgumentsForAllFourPortsInReverseOrder() {
        Main.main("-proxySecurePort", "4", "-proxyPort", "3", "-serverSecurePort", "2", "-serverPort", "1");

        verify(mockMockServerRunner).start(1, 2);
        verify(mockProxyRunner).start(3, 4);
    }

    @Test
    public void shouldParseArgumentsForProxyAndServerOnlyNonSecurePorts() {
        Main.main("-serverPort", "1", "-proxyPort", "2");

        verify(mockMockServerRunner).start(1, null);
        verify(mockProxyRunner).start(2, null);
    }

    @Test
    public void shouldParseArgumentsForProxyAndServerOnlySecurePorts() {
        Main.main("-serverSecurePort", "1", "-proxySecurePort", "2");

        verify(mockMockServerRunner).start(null, 1);
        verify(mockProxyRunner).start(null, 2);
    }

    @Test
    public void shouldParseArgumentsForServerOnlyBothPorts() {
        Main.main("-serverPort", "1", "-serverSecurePort", "2");

        verify(mockMockServerRunner).start(1, 2);
        verifyZeroInteractions(mockProxyRunner);
    }

    @Test
    public void shouldParseArgumentsForProxyOnlyBothPorts() {
        Main.main("-proxyPort", "1", "-proxySecurePort", "2");

        verifyZeroInteractions(mockMockServerRunner);
        verify(mockProxyRunner).start(1, 2);
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
        verifyZeroInteractions(mockMockServerRunner);
        verifyZeroInteractions(mockProxyRunner);
    }
}
