package org.mockserver.junit.jupiter;

import org.mockserver.integration.ClientAndServer;

import java.util.List;

public class ExtendedMockServerPerTestSuite extends MockServerExtension {

    public ExtendedMockServerPerTestSuite() {
        super();
        perTestSuite = true;
    }

    public ClientAndServer instantiateClient(List<Integer> ports) {
        return super.instantiateClient(ports);
    }


}
