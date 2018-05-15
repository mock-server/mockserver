package org.mockserver.junit.jupiter;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.integration.ClientAndServer;

import java.util.List;

@VisibleForTesting
class ClientAndServerFactory {
    ClientAndServer newClientAndServer(List<Integer> ports) {
        return ClientAndServer.startClientAndServer(ports.toArray(new Integer[0]));
    }
}