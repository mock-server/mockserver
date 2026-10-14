package org.mockserver.benchmark;

import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Mechanism proof (NOT a JMH benchmark) — a standalone verifier for the multi-source-address
 * binding {@link ConnectionCeilingBenchmark} uses so its connection ladder can be driven past the
 * <em>global ephemeral source-port</em> wall on macOS. The wall is a client limit, not the
 * server's; the only lever that moves it is more client source <b>addresses</b> (loopback aliases or
 * more load-generator hosts), each contributing its own ephemeral range.
 *
 * <p><b>What this proves without needing loopback aliases (which require root).</b> Creating a
 * non-primary loopback alias needs {@code sudo ifconfig lo0 alias 127.0.0.2 up}, so a ladder past
 * the wall is not run here. What is provable, and is proved:
 * <ol>
 *   <li><b>The round-robin distribution.</b> {@link ConnectionCeilingBenchmark#expectedRoundRobinCounts}
 *       deals connections evenly across addresses, remainder to the first few — the exact spread the
 *       driver produces and validates against.</li>
 *   <li><b>The per-address accounting and its guard.</b>
 *       {@link ConnectionCeilingBenchmark#assertSourceAddressDistribution} passes for a correct spread
 *       and THROWS both for a silent collapse onto one address and for a wrong per-address share —
 *       because a run that quietly used one address must never read as a raised ceiling.</li>
 *   <li><b>Source-address parsing.</b> Blank yields the single default {@code 127.0.0.1}; a list is
 *       preserved; a duplicate is rejected.</li>
 *   <li><b>The bindability preflight.</b> {@link ConnectionCeilingBenchmark#isBindable} is true for
 *       {@code 127.0.0.1} and false for a non-local address ({@code 203.0.113.1}, RFC 5737 TEST-NET-3),
 *       which is how a missing alias is caught up front instead of mid-rung.</li>
 *   <li><b>Explicit {@code 127.0.0.1} binding matches an unbound connect.</b> A real Netty connect with an
 *       explicit {@code 127.0.0.1:0} local bind lands on exactly the same local address as an unbound
 *       connect to a loopback destination, with a distinct OS-assigned port — so the default
 *       single-address run is unaffected by the binding, and the accounting reads the address
 *       the OS actually bound (from {@link Channel#localAddress()}), not the one requested.</li>
 * </ol>
 *
 * <p>Run standalone (prints {@code PROOF: PASS} / {@code PROOF: FAIL}, exits non-zero on failure):
 * <pre>java -cp "target/classes:$(cat target/classpath.txt)" org.mockserver.benchmark.ConnectionCeilingBindingProof</pre>
 */
public final class ConnectionCeilingBindingProof {

    /** RFC 5737 TEST-NET-3 — guaranteed not a local interface address on any host, so binding it fails. */
    private static final String NON_LOCAL_ADDRESS = "203.0.113.1";

    private static boolean ok = true;

    private ConnectionCeilingBindingProof() {
    }

    public static void main(String[] args) throws Exception {
        proveExpectedRoundRobinCounts();
        proveDistributionGuard();
        proveSourceAddressParsing();
        proveBindabilityPreflight();
        proveExplicitLoopbackBindIsIdenticalToDefault();

        System.out.println(ok ? "PROOF: PASS" : "PROOF: FAIL");
        if (!ok) {
            System.exit(1);
        }
    }

    /** Round-robin deals connections/addressCount to each, remainder one-each to the first few. */
    private static void proveExpectedRoundRobinCounts() {
        check("even split 6/3 -> [2,2,2]",
            java.util.Arrays.equals(ConnectionCeilingBenchmark.expectedRoundRobinCounts(6, 3), new int[]{2, 2, 2}));
        check("remainder 7/3 -> [3,2,2]",
            java.util.Arrays.equals(ConnectionCeilingBenchmark.expectedRoundRobinCounts(7, 3), new int[]{3, 2, 2}));
        check("single address 4/1 -> [4]",
            java.util.Arrays.equals(ConnectionCeilingBenchmark.expectedRoundRobinCounts(4, 1), new int[]{4}));
        check("fewer connections than addresses 2/4 -> [1,1,0,0]",
            java.util.Arrays.equals(ConnectionCeilingBenchmark.expectedRoundRobinCounts(2, 4), new int[]{1, 1, 0, 0}));
    }

    /** The distribution guard passes a correct spread and rejects both a collapse and a wrong share. */
    private static void proveDistributionGuard() throws Exception {
        InetAddress[] two = {InetAddress.getByName("127.0.0.1"), InetAddress.getByName("127.0.0.2")};

        // A correct round-robin spread of 6 across 2 addresses must be accepted.
        check("valid 3/3 spread accepted", noThrow(() ->
            ConnectionCeilingBenchmark.assertSourceAddressDistribution(counts("127.0.0.1", 3, "127.0.0.2", 3), two, 6)));

        // A silent collapse onto one address must be REJECTED — this is the false "raised ceiling".
        check("collapse onto one address rejected", throwsIllegalState(() ->
            ConnectionCeilingBenchmark.assertSourceAddressDistribution(counts("127.0.0.1", 6, "127.0.0.2", 0), two, 6)));

        // A wrong per-address share (both addresses used, but not evenly) must be REJECTED.
        check("uneven 4/2 share rejected", throwsIllegalState(() ->
            ConnectionCeilingBenchmark.assertSourceAddressDistribution(counts("127.0.0.1", 4, "127.0.0.2", 2), two, 6)));

        // Single-address default: all connections on one address is correct, not a collapse.
        InetAddress[] one = {InetAddress.getByName("127.0.0.1")};
        check("single-address all-on-one accepted", noThrow(() ->
            ConnectionCeilingBenchmark.assertSourceAddressDistribution(counts("127.0.0.1", 5), one, 5)));
    }

    private static void proveSourceAddressParsing() throws Exception {
        InetAddress[] blank = ConnectionCeilingBenchmark.parseSourceAddresses("");
        check("blank -> single 127.0.0.1",
            blank.length == 1 && blank[0].getHostAddress().equals("127.0.0.1"));

        InetAddress[] list = ConnectionCeilingBenchmark.parseSourceAddresses("127.0.0.1, 127.0.0.2 ,127.0.0.3");
        check("list preserved in order", list.length == 3
            && list[0].getHostAddress().equals("127.0.0.1")
            && list[1].getHostAddress().equals("127.0.0.2")
            && list[2].getHostAddress().equals("127.0.0.3"));

        check("duplicate address rejected", throwsIllegalArgument(() ->
            ConnectionCeilingBenchmark.parseSourceAddresses("127.0.0.1,127.0.0.1")));
    }

    private static void proveBindabilityPreflight() throws Exception {
        check("127.0.0.1 is bindable",
            ConnectionCeilingBenchmark.isBindable(InetAddress.getByName("127.0.0.1")));
        check(NON_LOCAL_ADDRESS + " is NOT bindable (stands in for a missing alias)",
            !ConnectionCeilingBenchmark.isBindable(InetAddress.getByName(NON_LOCAL_ADDRESS)));
    }

    /**
     * The load-bearing behavioural equivalence: an explicit {@code 127.0.0.1:0} bind and an unbound
     * connect to a loopback destination land on the same local address, and repeated explicit binds get
     * distinct OS-assigned ports — so the default single-address run is unchanged and the tally reads
     * the address the OS bound. Uses a plain accept-only {@link ServerSocket}; the TCP connect is all
     * the driver's {@code connect(...)} needs to establish a channel and expose its local address.
     */
    private static void proveExplicitLoopbackBindIsIdenticalToDefault() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        Thread acceptor = new Thread(() -> {
            try {
                //noinspection InfiniteLoopStatement
                while (true) {
                    server.accept(); // hold the accepted sockets open; never read or write
                }
            } catch (Exception ignored) {
                // server closed at end of proof
            }
        }, "ceiling-proof-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        int port = server.getLocalPort();

        try {
            Channel unbound = ConnectionCeilingBenchmark.connect(group, null, "127.0.0.1", port, null);
            Channel explicit = ConnectionCeilingBenchmark.connect(group, null, "127.0.0.1", port,
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            try {
                InetSocketAddress unboundLocal = (InetSocketAddress) unbound.localAddress();
                InetSocketAddress explicitLocal = (InetSocketAddress) explicit.localAddress();
                check("unbound connect lands on 127.0.0.1",
                    unboundLocal.getAddress().getHostAddress().equals("127.0.0.1"));
                check("explicit 127.0.0.1 bind lands on the SAME address as unbound",
                    explicitLocal.getAddress().equals(unboundLocal.getAddress()));
                check("explicit bind got a distinct OS-assigned ephemeral port",
                    explicitLocal.getPort() != 0 && explicitLocal.getPort() != unboundLocal.getPort());
            } finally {
                unbound.close().sync();
                explicit.close().sync();
            }

            // Round-robin several explicit binds over the single default address and build the tally the
            // same way measureRung does — distinct endpoints, and a distribution the guard accepts.
            InetAddress[] sources = {InetAddress.getByName("127.0.0.1")};
            int connections = 8;
            Set<InetSocketAddress> distinctEndpoints = new LinkedHashSet<>();
            Map<String, Integer> tally = new LinkedHashMap<>();
            tally.put("127.0.0.1", 0);
            java.util.List<Channel> channels = new java.util.ArrayList<>();
            try {
                for (int i = 0; i < connections; i++) {
                    InetAddress source = sources[i % sources.length];
                    Channel channel = ConnectionCeilingBenchmark.connect(group, null, "127.0.0.1", port,
                        new InetSocketAddress(source, 0));
                    channels.add(channel);
                    InetSocketAddress local = (InetSocketAddress) channel.localAddress();
                    distinctEndpoints.add(local);
                    tally.merge(local.getAddress().getHostAddress(), 1, Integer::sum);
                }
                check("all " + connections + " local endpoints are distinct (address+port)",
                    distinctEndpoints.size() == connections);
                check("tally matches round-robin over the single default address", noThrow(() ->
                    ConnectionCeilingBenchmark.assertSourceAddressDistribution(tally, sources, connections)));
            } finally {
                for (Channel channel : channels) {
                    channel.close();
                }
            }
        } finally {
            server.close();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    private static Map<String, Integer> counts(Object... addressThenCount) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < addressThenCount.length; i += 2) {
            map.put((String) addressThenCount[i], (Integer) addressThenCount[i + 1]);
        }
        return map;
    }

    private static boolean noThrow(Executable executable) {
        try {
            executable.execute();
            return true;
        } catch (Throwable t) {
            System.err.println("  unexpected throw: " + t);
            return false;
        }
    }

    private static boolean throwsIllegalState(Executable executable) {
        return throwsType(executable, IllegalStateException.class);
    }

    private static boolean throwsIllegalArgument(Executable executable) {
        return throwsType(executable, IllegalArgumentException.class);
    }

    private static boolean throwsType(Executable executable, Class<? extends Throwable> expected) {
        try {
            executable.execute();
            return false;
        } catch (Throwable t) {
            return expected.isInstance(t);
        }
    }

    private static void check(String description, boolean condition) {
        System.out.println((condition ? "  ok   " : "  FAIL ") + description);
        ok &= condition;
    }

    /** A throwing no-arg action, so negative cases can assert the exact exception type. */
    @FunctionalInterface
    private interface Executable {
        void execute() throws Throwable;
    }
}
