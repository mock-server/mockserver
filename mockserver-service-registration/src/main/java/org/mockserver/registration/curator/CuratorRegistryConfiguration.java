package org.mockserver.registration.curator;

import java.net.InetSocketAddress;
import java.util.Set;

/**
 * Configuration used by the Curator service registry.
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public class CuratorRegistryConfiguration {

    private Set<InetSocketAddress> zookeeperAddresses;
    private String zookeeperBasePath;
    private int mockserverPort;

    public CuratorRegistryConfiguration() {
    }

    public Set<InetSocketAddress> getZookeeperAddresses() {
        return this.zookeeperAddresses;
    }

    public CuratorRegistryConfiguration setZookeeperAddresses(Set<InetSocketAddress> zookeeperAddresses) {
        this.zookeeperAddresses = zookeeperAddresses;
        return this;
    }

    public String getZookeeperBasePath() {
        return this.zookeeperBasePath;
    }

    public CuratorRegistryConfiguration setZookeeperBasePath(String zookeeperBasePath) {
        this.zookeeperBasePath = zookeeperBasePath;
        return this;
    }

    public int getMockserverPort() {
        return mockserverPort;
    }

    public CuratorRegistryConfiguration setMockserverPort(int mockserverPort) {
        this.mockserverPort = mockserverPort;
        return this;
    }
}
