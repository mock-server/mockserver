package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Protocol;
import org.mockserver.testing.integration.mock.AbstractBasicMockingSameJVMIntegrationTest;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class HTTP2MockingIntegrationTest extends AbstractBasicMockingSameJVMIntegrationTest {

    private static int mockServerPort;

    @BeforeClass
    public static void startServer() {
        mockServerClient = startClientAndServer();
        mockServerPort = mockServerClient.getPort();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

    public HttpRequest getRequestModifier(HttpRequest httpRequest) {
        // TODO(jamesdbloom) support http2 in plain text
        if (Boolean.TRUE.equals(httpRequest.isSecure())) {
            return httpRequest
                .clone()
                .withProtocol(Protocol.HTTP_2);
        } else {
            return httpRequest;
        }
    }

}
