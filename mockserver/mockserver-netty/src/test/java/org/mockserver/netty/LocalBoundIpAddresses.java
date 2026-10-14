package org.mockserver.netty;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Addresses for tests of {@code localBoundIP}, which bind MockServer to the loopback address and then send to
 * another address of this host, which a listener on every address would also answer.
 */
public final class LocalBoundIpAddresses {

    private static final int PROBE_TIMEOUT_MILLIS = 1000;

    private LocalBoundIpAddresses() {
    }

    /**
     * @return an IPv4 address of an interface of this host that is up, not the loopback, not a point-to-point or
     * virtual interface, and that a datagram sent from this host reaches; if there is none, fails the test on CI
     * (so the suite cannot pass without running) and skips it elsewhere, as on a host whose VPN captures traffic
     * to its own addresses
     */
    public static InetAddress anotherAddressOfThisHost() throws Exception {
        List<String> rejections = new ArrayList<>();
        for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            String rejection = interfaceRejection(networkInterface);
            if (rejection != null) {
                rejections.add(networkInterface.getName() + ": " + rejection);
                continue;
            }
            for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                if (!(address instanceof Inet4Address)) {
                    continue;
                }
                if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                    rejections.add(networkInterface.getName() + " " + address.getHostAddress() + ": loopback or link-local");
                } else if (reachedByADatagramFromThisHost(address)) {
                    return address;
                } else {
                    rejections.add(networkInterface.getName() + " " + address.getHostAddress() + ": a datagram from this host did not arrive within " + PROBE_TIMEOUT_MILLIS + "ms");
                }
            }
        }
        noAddressQualifies(rejections, System.getenv());
        return null;
    }

    static void noAddressQualifies(List<String> rejections, Map<String, String> environment) {
        String message = "this test needs an IPv4 address of this host, other than the loopback, that a datagram from this host reaches, and there is none; interfaces seen: " + rejections;
        if (onCi(environment)) {
            fail(message);
        }
        assumeTrue(message, false);
    }

    private static boolean onCi(Map<String, String> environment) {
        return isSet(environment.get("BUILDKITE")) || isSet(environment.get("CI"));
    }

    private static boolean isSet(String value) {
        return value != null && !value.isEmpty() && !value.equalsIgnoreCase("false");
    }

    private static String interfaceRejection(NetworkInterface networkInterface) throws Exception {
        if (!networkInterface.isUp()) {
            return "down";
        } else if (networkInterface.isLoopback()) {
            return "loopback";
        } else if (networkInterface.isPointToPoint()) {
            return "point-to-point, such as a VPN tunnel";
        } else if (networkInterface.isVirtual()) {
            return "virtual";
        }
        return null;
    }

    private static boolean reachedByADatagramFromThisHost(InetAddress address) {
        // both bound to the IPv4 address under test, never to every address of a dual-stack socket
        try (DatagramSocket receiver = new DatagramSocket(new InetSocketAddress(address, 0)); DatagramSocket sender = new DatagramSocket(new InetSocketAddress(address, 0))) {
            receiver.setSoTimeout(PROBE_TIMEOUT_MILLIS);
            sender.send(new DatagramPacket(new byte[]{1}, 1, address, receiver.getLocalPort()));
            receiver.receive(new DatagramPacket(new byte[1], 1));
            return true;
        } catch (Exception unreachable) {
            return false;
        }
    }
}
