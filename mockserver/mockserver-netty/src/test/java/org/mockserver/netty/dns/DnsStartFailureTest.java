package org.mockserver.netty.dns;

import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.lifecycle.Ipv4UdpPortProbe;
import org.mockserver.lifecycle.RefusedStart.RecordingMockServer;
import org.mockserver.lifecycle.RefusedStart.Started;
import org.mockserver.model.DnsRecord;
import org.mockserver.model.DnsResponse;
import org.mockserver.netty.MockServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.lifecycle.RefusedStart.assertStopped;
import static org.mockserver.lifecycle.RefusedStart.refusedStart;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;
import static org.mockserver.netty.dns.DnsQueries.addressAnsweredFor;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A server that cannot serve DNS on the {@code dnsPort} it was given must refuse to start, as it does for a TCP
 * port it cannot bind, and leave nothing running: the caller gets no reference it could stop.
 */
public class DnsStartFailureTest {

    // only a dual-stack bind on macOS succeeds on such a port; elsewhere the bind fails with its own error
    private static final boolean DUAL_STACK_MAC_OS = System.getProperty("os.name", "").toLowerCase().contains("mac")
        && !Boolean.getBoolean("java.net.preferIPv4Stack");

    private static final String SERVED_NAME = "served.dns-start.example.";
    private static final String SERVED_ADDRESS = "10.9.8.7";

    // a socket on 127.0.0.1 fails the server's bind of that port on every address, on every platform
    private static DatagramChannel heldUdpPort() throws Exception {
        DatagramChannel otherApplication = DatagramChannel.open(StandardProtocolFamily.INET);
        otherApplication.bind(new InetSocketAddress("127.0.0.1", 0));
        return otherApplication;
    }

    private static int port(DatagramChannel channel) throws Exception {
        return ((InetSocketAddress) channel.getLocalAddress()).getPort();
    }

    private static Configuration dnsOn(int dnsPort) {
        return configuration().dnsEnabled(true).dnsPort(dnsPort);
    }

    @Test
    public void shouldRefuseToStartWhenTheDnsPortIsHeldNamingThePortAndTheCauseOnOneLine() throws Exception {
        try (DatagramChannel otherApplication = heldUdpPort()) {
            int udpPort = port(otherApplication);

            Throwable refused = refusedStart(() -> new MockServer(dnsOn(udpPort), 0));

            assertThat(refused, instanceOf(DnsStartupException.class));
            assertThat(refused.getMessage(), startsWith(portCouldNotBeOpenedOrBound(udpPort)));
            // the operating system's own words for the port being in use, under the exception of the transport in use
            assertThat(refused.getMessage(), endsWith("Address already in use)"));
            assertThat("one line", refused.getMessage(), not(containsString("\n")));
            assertThat(refused.getCause(), instanceOf(IOException.class));
            assertThat(refused.getCause().getMessage(), containsString("Address already in use"));
            assertThat(((DnsStartupException) refused).isPortUnavailable(), is(true));
        }
    }

    // on macOS the server's own bind succeeds there, and the other socket would then get the queries sent to 127.0.0.1
    @Test
    public void shouldRefuseToStartWhenTheDnsPortIsHeldOnTheIpv4Wildcard() throws Exception {
        Started started = new Started();
        try (DatagramChannel otherApplication = DatagramChannel.open(StandardProtocolFamily.INET)) {
            otherApplication.bind(new InetSocketAddress("0.0.0.0", 0));
            int udpPort = port(otherApplication);

            Throwable refused = refusedStart(() -> new RecordingMockServer(started, dnsOn(udpPort)), () -> {
                assertStopped(started);
                otherApplication.close();
                assertThat("the refused server must hold no socket on UDP port " + udpPort, Ipv4UdpPortProbe.dualStackBindSucceeds(udpPort), is(true));
            });

            assertThat(refused, instanceOf(DnsStartupException.class));
            assertThat(refused.getMessage(), startsWith(portCouldNotBeOpenedOrBound(udpPort)));
            assertThat(((DnsStartupException) refused).isPortUnavailable(), is(true));
            if (DUAL_STACK_MAC_OS) {
                assertThat(refused.getMessage(), endsWith("BindException: UDP port " + udpPort + " is already in use by another socket listening on 0.0.0.0:" + udpPort
                    + ", so DNS queries to localhost:" + udpPort + " would reach that socket instead of MockServer"
                    + "; stop the application that holds it or choose a different dnsPort (to find it run: lsof -nP -iUDP:" + udpPort + "))"));
            }
        }
    }

    @Test
    public void shouldHaveClosedItsTcpListenerAndHoldNoUdpSocketWhenTheConstructorThrows() throws Exception {
        Started started = new Started();
        try (DatagramChannel otherApplication = heldUdpPort()) {
            int udpPort = port(otherApplication);

            refusedStart(() -> new RecordingMockServer(started, dnsOn(udpPort)), () -> {
                assertStopped(started);
                // only the other application held the port, so with it gone a socket left there is the server's
                otherApplication.close();
                try (DatagramChannel sameBindAgain = DatagramChannel.open(StandardProtocolFamily.INET)) {
                    sameBindAgain.bind(new InetSocketAddress("127.0.0.1", udpPort));
                }
            });
        }
    }

    // stop() does not wait on an interrupted thread, and a JUnit timeout interrupts the thread that is starting a server
    @Test
    public void shouldStopAndKeepTheInterruptWhenTheStartingThreadIsInterrupted() throws Exception {
        Started started = new Started();
        started.interruptOnceTcpIsBound = true;
        AtomicBoolean interruptedAtTheThrow = new AtomicBoolean();
        try (DatagramChannel otherApplication = heldUdpPort()) {
            int udpPort = port(otherApplication);

            Throwable refused = refusedStart(() -> new RecordingMockServer(started, dnsOn(udpPort)), () -> {
                interruptedAtTheThrow.set(Thread.interrupted());
                assertStopped(started);
            });

            assertThat(refused, instanceOf(DnsStartupException.class));
            assertThat("the caller must still see the interrupt", interruptedAtTheThrow.get(), is(true));
        }
    }

    @Test
    public void shouldRefuseASecondMockServerGivenTheDnsPortOfTheFirst() throws Exception {
        MockServer first = new MockServer(dnsOn(0), 0);
        try {
            int udpPort = first.getDnsPort();

            Throwable refused = refusedStart(() -> new MockServer(dnsOn(udpPort), 0));

            assertThat(refused, instanceOf(DnsStartupException.class));
            assertThat(refused.getMessage(), startsWith(portCouldNotBeOpenedOrBound(udpPort)));
            assertThat("the first server keeps the port", first.getDnsPort(), is(udpPort));
            assertThat(first.isRunning(), is(true));
        } finally {
            first.stop();
        }
    }

    // its cause is an IllegalArgumentException, which the run command takes for a usage error and exits 0
    @Test
    public void shouldRefuseToStartWhenTheDnsPortIsNotAPort() throws Exception {
        Throwable refused = refusedStart(() -> new MockServer(dnsOn(70000), 0));

        assertThat(refused, instanceOf(DnsStartupException.class));
        assertThat(refused.getMessage(), is("DNS mocking is enabled (dnsEnabled=true, dnsPort=70000) but its server could not start on UDP port 70000, so MockServer cannot start:"
            + " fix the underlying error or set dnsEnabled=false to run without DNS mocking (underlying error: IllegalArgumentException: port out of range:70000)"));
        assertThat(refused.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(((DnsStartupException) refused).isPortUnavailable(), is(false));
    }

    @Test
    public void shouldPropagateTheRefusalFromStartClientAndServer() throws Exception {
        try (DatagramChannel otherApplication = heldUdpPort()) {
            int udpPort = port(otherApplication);

            Throwable refused = refusedStart(() -> ClientAndServer.startClientAndServer(dnsOn(udpPort), 0));

            assertThat(refused, instanceOf(DnsStartupException.class));
            assertThat(refused.getMessage(), containsString("UDP port " + udpPort + " could not be opened or bound"));
        }
    }

    @Test
    public void shouldServeDnsOnAPortTheOperatingSystemChoosesAndReportIt() throws Exception {
        MockServer server = new MockServer(dnsOn(0), 0);
        MockServerClient client = new MockServerClient("127.0.0.1", server.getLocalPort());
        try {
            assertThat("the port the operating system chose", server.getDnsPort(), greaterThan(0));
            client.when(dnsRequest(SERVED_NAME)).respondWithDns(new DnsResponse().withAnswerRecords(DnsRecord.aRecord(SERVED_NAME, SERVED_ADDRESS)));

            assertThat(addressAnsweredFor(SERVED_NAME, server.getDnsPort()), is(SERVED_ADDRESS));
        } finally {
            stopQuietly(client);
            server.stop();
        }
    }

    @Test
    public void shouldStartWithoutDnsAndLeaveTheDnsPortAloneWhenDnsIsDisabled() throws Exception {
        try (DatagramChannel otherApplication = heldUdpPort()) {
            MockServer server = new MockServer(configuration().dnsEnabled(false).dnsPort(port(otherApplication)), 0);
            try {
                assertThat(server.isRunning(), is(true));
                assertThat("no DNS port is bound", server.getDnsPort(), is(-1));
            } finally {
                server.stop();
            }
        }
    }

    // a real "Permission denied" needs a Linux host that withholds the port, such as a container run as a non-root user
    @Test
    public void shouldTellAProcessThatMayNotBindAPortBelow1024HowToGetOne() {
        DnsStartupException refused = DnsStartupException.portCouldNotBeOpenedOrBound(53, new java.net.BindException("Permission denied"));

        assertThat(refused.getMessage(), is(privilegedPortNotPermitted(53) + "BindException: Permission denied)"));
        assertThat(refused.isPortUnavailable(), is(true));
    }

    @Test
    public void shouldTellAProcessThatMayNotBindAPortBelow1024HowToGetOneOnEpoll() {
        DnsStartupException refused = DnsStartupException.portCouldNotBeOpenedOrBound(53, new IOException("bind(..) failed with error(-13): Permission denied"));

        assertThat(refused.getMessage(), is(privilegedPortNotPermitted(53) + "IOException: bind(..) failed with error(-13): Permission denied)"));
    }

    @Test
    public void shouldNotBlamePrivilegesForAPortBelow1024ThatIsHeld() {
        DnsStartupException refused = DnsStartupException.portCouldNotBeOpenedOrBound(53, new IOException("bind(..) failed with error(-98): Address already in use"));

        assertThat(refused.getMessage(), is(portCouldNotBeOpenedOrBound(53) + "IOException: bind(..) failed with error(-98): Address already in use)"));
    }

    @Test
    public void shouldNotBlameThePortNumberForAPermissionDeniedOnAPortFrom1024() {
        DnsStartupException refused = DnsStartupException.portCouldNotBeOpenedOrBound(1024, new java.net.BindException("Permission denied"));

        assertThat(refused.getMessage(), is(portCouldNotBeOpenedOrBound(1024) + "BindException: Permission denied)"));
    }

    // dnsPort 0: every port MockServer chose was refused, so no port below 1024 is to blame
    @Test
    public void shouldNotBlameThePortNumberWhenNoChosenPortCouldBeBound() {
        DnsStartupException refused = DnsStartupException.serverCouldNotStart(0, new java.net.BindException("Permission denied"));

        assertThat(refused.getMessage(), is("DNS mocking is enabled (dnsEnabled=true, dnsPort=0) but its server could not start on UDP port 0, so MockServer cannot start:"
            + " fix the underlying error or set dnsEnabled=false to run without DNS mocking (underlying error: BindException: Permission denied)"));
    }

    // the epoll transport, the default on Linux, reports a failed bind with an IOException of its own
    @Test
    public void shouldNameAPortWhoseBindFailedWithAnyException() {
        DnsStartupException refused = DnsStartupException.portCouldNotBeOpenedOrBound(5353, new IOException("bind(..) failed with error(-98): Address already in use"));

        assertThat(refused.getMessage(), is(portCouldNotBeOpenedOrBound(5353) + "IOException: bind(..) failed with error(-98): Address already in use)"));
        assertThat(refused.isPortUnavailable(), is(true));
    }

    private static String portCouldNotBeOpenedOrBound(int udpPort) {
        return "DNS mocking is enabled (dnsEnabled=true, dnsPort=" + udpPort + ") but UDP port " + udpPort + " could not be opened or bound, so MockServer cannot start:"
            + " free the port if another application holds it, choose a different dnsPort (0 picks a free port, and a port below 1024 can need extra privileges),"
            + " or set dnsEnabled=false to run without DNS mocking (underlying error: ";
    }

    private static String privilegedPortNotPermitted(int udpPort) {
        return "DNS mocking is enabled (dnsEnabled=true, dnsPort=" + udpPort + ") but this process is not permitted to bind UDP port " + udpPort
            + ", a port below 1024, so MockServer cannot start: choose a dnsPort of 1024 or above (in Docker, publish it as port " + udpPort
            + ", for example -p " + udpPort + ":5053/udp with dnsPort=5053), run MockServer as root, or lower the sysctl net.ipv4.ip_unprivileged_port_start"
            + " to " + udpPort + " (adding the NET_BIND_SERVICE capability to a container does not give it to a non-root user),"
            + " or set dnsEnabled=false to run without DNS mocking (underlying error: ";
    }
}
