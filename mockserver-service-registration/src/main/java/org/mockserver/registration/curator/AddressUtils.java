package org.mockserver.registration.curator;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Utility class for working with InetSocketAddress class.
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public final class AddressUtils {

    public static final Joiner COMMA_JOINER = Joiner.on(",");
    public static final Joiner COLON_JOINER = Joiner.on(":");
    public static final int ZOOKEEPER_PORT = 2181;

    //For IP address lookup
    public static final List<String> DEFAULT_INTERFACES = ImmutableList.of("en0", "eth0");
    public static final String DEFAULT_HOST_NAME = "localhost";

    private AddressUtils() {
    }

    // Convert to zookeeper connection string
    public static String toZookeeperConnectionString(Collection<InetSocketAddress> zookeeperAddresses) {
        ImmutableList.Builder<String> connectionStrings = ImmutableList.builder();
        for (InetSocketAddress zookeeperAddress : zookeeperAddresses) {
            connectionStrings.add(toStandardAddressScheme(zookeeperAddress));
        }
        return COMMA_JOINER.join(connectionStrings.build());
    }

    // Convert to standard scheme for addresses in this project
    public static String toStandardAddressScheme(InetSocketAddress address) {
        return COLON_JOINER.join(address.getAddress().getHostAddress(), address.getPort());
    }

    // Get the localhost's network address
    public static InetSocketAddress getLocalHostAddress(int port) {
        try {
            for (String name : DEFAULT_INTERFACES) {
                NetworkInterface networkInterface = NetworkInterface.getByName(name);
                if (networkInterface != null) {
                    for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                        if (inetAddress instanceof Inet4Address) {
                            return new InetSocketAddress(inetAddress.getHostAddress(), port);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException("Unable to retrieve hostname by interface.");
        }
        return new InetSocketAddress(DEFAULT_HOST_NAME, port);
    }

    // Get a list of socket addresses from host names using the port
    public static Set<InetSocketAddress> toAddresses(List<String> servers, int port) {
        ImmutableSet.Builder<InetSocketAddress> addresses = ImmutableSet.builder();
        for (String server : servers) {
            addresses.add(new InetSocketAddress(server, port));
        }
        return addresses.build();
    }
}
