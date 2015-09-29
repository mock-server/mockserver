package org.mockserver.cli;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;
import org.mockserver.stop.StopEventQueue;

import java.io.PrintStream;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MainTest {

    private final static Integer SERVER_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_REMOTE_PORT = PortFactory.findFreePort();

    private MockServerBuilder previousMockServerBuilder;
    private ProxyBuilder previousProxyBuilder;
    private PrintStream previousPrintStream;
    private Runtime previousRuntime;

    @Mock
    private MockServerBuilder mockMockServerBuilder;
    @Mock
    private ProxyBuilder mockProxyBuilder;
    @Mock
    private PrintStream mockPrintStream;
    @Mock
    private Runtime mockRuntime;

    @Before
    public void setupMocks() {
        initMocks(this);

        previousMockServerBuilder = Main.mockServerBuilder;
        previousProxyBuilder = Main.httpProxyBuilder;
        previousPrintStream = Main.outputPrintStream;
        previousRuntime = Main.runtime;

        Main.mockServerBuilder = mockMockServerBuilder;
        Main.httpProxyBuilder = mockProxyBuilder;
        Main.outputPrintStream = mockPrintStream;
        Main.runtime = mockRuntime;

        when(mockMockServerBuilder.withStopEventQueue(any(StopEventQueue.class))).thenReturn(mockMockServerBuilder);
        when(mockMockServerBuilder.withHTTPPort(anyInt())).thenReturn(mockMockServerBuilder);

        when(mockProxyBuilder.withStopEventQueue(any(StopEventQueue.class))).thenReturn(mockProxyBuilder);
        when(mockProxyBuilder.withLocalPort(anyInt())).thenReturn(mockProxyBuilder);
    }

    @After
    public void cleanUpFixture() {
        Main.mockServerBuilder = previousMockServerBuilder;
        Main.httpProxyBuilder = previousProxyBuilder;
        Main.outputPrintStream = previousPrintStream;
        Main.runtime = previousRuntime;
    }

    @Test
    public void shouldParseArgumentsForAllPorts() {
        Main.main("-serverPort", SERVER_PORT.toString(), "-proxyPort", PROXY_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForAllPortsAndDirectProxyWithHostname() {
        Main.main("-serverPort", SERVER_PORT.toString(), "-proxyPort", PROXY_PORT.toString(), "-proxyRemotePort", PROXY_REMOTE_PORT.toString(), "-proxyRemoteHost", "otherhost");

        verify(mockMockServerBuilder).withHTTPPort(SERVER_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).withDirect("otherhost", PROXY_REMOTE_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForAllPortsAndDirectProxyNoHostname() {
        Main.main("-serverPort", SERVER_PORT.toString(), "-proxyPort", PROXY_PORT.toString(), "-proxyRemotePort", PROXY_REMOTE_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).withDirect("localhost", PROXY_REMOTE_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForAllPortsAndDirectProxyHostnameButNoPort() {
        Main.main("-serverPort", SERVER_PORT.toString(), "-proxyPort", PROXY_PORT.toString(), "-proxyRemoteHost", "otherhost");

        verify(mockMockServerBuilder).withHTTPPort(SERVER_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForAllPortsInReverseOrder() {
        Main.main("-proxyPort", PROXY_PORT.toString(), "-serverPort", SERVER_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_PORT);
        verify(mockMockServerBuilder).build();
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForServerOnly() {
        Main.main("-serverPort", SERVER_PORT.toString());

        verify(mockMockServerBuilder).withHTTPPort(SERVER_PORT);
        verify(mockMockServerBuilder).build();
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldParseArgumentsForProxyOnly() {
        Main.main("-proxyPort", PROXY_PORT.toString());

        verifyZeroInteractions(mockMockServerBuilder);
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForDirectProxyWithNoHostnameOnly() {
        Main.main("-proxyPort", PROXY_PORT.toString(), "-proxyRemotePort", PROXY_REMOTE_PORT.toString());

        verifyZeroInteractions(mockMockServerBuilder);
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).withDirect("localhost", PROXY_REMOTE_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForDirectProxyWithTextHostnameOnly() {
        Main.main("-proxyPort", PROXY_PORT.toString(), "-proxyRemotePort", PROXY_REMOTE_PORT.toString(), "-proxyRemoteHost", "otherhost");

        verifyZeroInteractions(mockMockServerBuilder);
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).withDirect("otherhost", PROXY_REMOTE_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsForDirectProxyWithIpHostnameOnly() {
        Main.main("-proxyPort", PROXY_PORT.toString(), "-proxyRemotePort", PROXY_REMOTE_PORT.toString(), "-proxyRemoteHost", "127.0.0.1");

        verifyZeroInteractions(mockMockServerBuilder);
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).withDirect("127.0.0.1", PROXY_REMOTE_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldParseArgumentsWithDirectProxyHostnameAndNoRemotePort() {
        Main.main("-proxyPort", PROXY_PORT.toString(), "-proxyRemoteHost", "otherhost");

        verifyZeroInteractions(mockMockServerBuilder);
        verify(mockProxyBuilder).withLocalPort(PROXY_PORT);
        verify(mockProxyBuilder).build();
    }

    @Test
    public void shouldPrintOutUsageForInvalidPort() {
        Main.main("-proxyPort", "1", "-invalidOption", "2");

        verify(mockPrintStream, times(1)).print(Main.USAGE);
        verify(mockRuntime, times(1)).exit(1);
        verifyZeroInteractions(mockMockServerBuilder);
    }

    @Test
    public void shouldPrintOutUsageForMissingLastPort() {
        Main.main("-serverPort", "1", "-proxyPort");

        verify(mockPrintStream, times(1)).print(Main.USAGE);
        verify(mockRuntime, times(1)).exit(1);
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldPrintOutUsageForInvalidServerPort() {
        Main.main("-serverPort", "A", "-proxyPort", "1");

        verify(mockPrintStream, times(1)).print(Main.USAGE);
        verify(mockPrintStream, times(1)).println(System.getProperty("line.separator") + "   ==================================================================");
        verify(mockPrintStream, times(1)).println("   serverPort value \"A\" is invalid, please specify a port i.e. \"1080\"");
        verify(mockPrintStream, times(1)).println("   ==================================================================" + System.getProperty("line.separator"));
        verify(mockRuntime, times(1)).exit(1);
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldPrintOutUsageForInvalidProxyPort() {
        Main.main("-serverPort", "1", "-proxyPort", "A");

        verify(mockPrintStream, times(1)).print(Main.USAGE);
        verify(mockPrintStream, times(1)).println(System.getProperty("line.separator") + "   =================================================================");
        verify(mockPrintStream, times(1)).println("   proxyPort value \"A\" is invalid, please specify a port i.e. \"1080\"");
        verify(mockPrintStream, times(1)).println("   =================================================================" + System.getProperty("line.separator"));
        verify(mockRuntime, times(1)).exit(1);
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldPrintOutUsageForInvalidProxyRemotePort() {
        Main.main("-serverPort", "1", "-proxyPort", "2", "-proxyRemotePort", "A", "-proxyRemoteHost", "1234567890");

        verify(mockPrintStream, times(1)).print(Main.USAGE);
        verify(mockPrintStream, times(1)).println(System.getProperty("line.separator") + "   =======================================================================");
        verify(mockPrintStream, times(1)).println("   proxyRemotePort value \"A\" is invalid, please specify a port i.e. \"1080\"");
        verify(mockPrintStream, times(1)).println("   =======================================================================" + System.getProperty("line.separator"));
        verify(mockRuntime, times(1)).exit(1);
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldPrintOutUsageForInvalidProxyRemoteHost() {
        Main.main("-serverPort", "1", "-proxyPort", "2", "-proxyRemotePort", "3", "-proxyRemoteHost", "http://localhost");

        verify(mockPrintStream, times(1)).print(Main.USAGE);
        verify(mockPrintStream, times(1)).println(System.getProperty("line.separator") + "   ===============================================================================================================");
        verify(mockPrintStream, times(1)).println("   proxyRemoteHost value \"http://localhost\" is invalid, please specify a host name i.e. \"localhost\" or \"127.0.0.1\"");
        verify(mockPrintStream, times(1)).println("   ===============================================================================================================" + System.getProperty("line.separator"));
        verify(mockRuntime, times(1)).exit(1);
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldPrintOutUsageForMultipleInvalidArguments() {
        Main.main("-serverPort", "A", "-proxyPort", "B", "-proxyRemotePort", "C", "-proxyRemoteHost", "http://localhost");

        verify(mockPrintStream, times(1)).print(Main.USAGE);
        verify(mockPrintStream, times(1)).println(System.getProperty("line.separator") + "   ===============================================================================================================");
        verify(mockPrintStream, times(1)).println("   serverPort value \"A\" is invalid, please specify a port i.e. \"1080\"");
        verify(mockPrintStream, times(1)).println("   proxyPort value \"B\" is invalid, please specify a port i.e. \"1080\"");
        verify(mockPrintStream, times(1)).println("   proxyRemotePort value \"C\" is invalid, please specify a port i.e. \"1080\"");
        verify(mockPrintStream, times(1)).println("   proxyRemoteHost value \"http://localhost\" is invalid, please specify a host name i.e. \"localhost\" or \"127.0.0.1\"");
        verify(mockPrintStream, times(1)).println("   ===============================================================================================================" + System.getProperty("line.separator"));
        verify(mockRuntime, times(1)).exit(1);
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldPrintOutUsageForMissingFirstPort() {
        Main.main("-serverPort", "-proxyPort", "2");

        verify(mockPrintStream, times(1)).print(Main.USAGE);
        verify(mockRuntime, times(1)).exit(1);
        verifyZeroInteractions(mockMockServerBuilder);
        verifyZeroInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldPrintOutUsageForNoArguments() {
        // using non static reference and constructor for coverage
        new Main().main();

        verify(mockPrintStream, times(1)).print(Main.USAGE);
        verify(mockRuntime, times(1)).exit(1);
        verifyZeroInteractions(mockMockServerBuilder);
        verifyZeroInteractions(mockProxyBuilder);
    }
}
