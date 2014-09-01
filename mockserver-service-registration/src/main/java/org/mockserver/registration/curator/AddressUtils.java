package org.mockserver.registration.curator;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.net.InetSocketAddress;
import java.util.Collection;

/**
 * Utility class for working with InetSocketAddress class.
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public final class AddressUtils {

    public static final Joiner COMMA_JOINER = Joiner.on(",");
    public static final Joiner COLON_JOINER = Joiner.on(":");

    private AddressUtils() {
    }

    public static String toZookeeperConnectionString(Collection<InetSocketAddress> zookeeperAddresses) {
        ImmutableList.Builder<String> connectionStrings = ImmutableList.builder();
        for (InetSocketAddress zookeeperAddress : zookeeperAddresses) {
            connectionStrings.add(toStandardAddressScheme(zookeeperAddress));
        }
        return COMMA_JOINER.join(connectionStrings.build());
    }

    public static String toStandardAddressScheme(InetSocketAddress address) {
        return COLON_JOINER.join(address.getAddress().getHostAddress(), address.getPort());
    }
}
