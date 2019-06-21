package org.mockserver.lifecycle;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class PortBindingIntegrationTest {

    private final static int MOCK_SERVER_PORT = PortFactory.findFreePort();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void throwsExpectionOnPortAlreadyBound() {
        MockServer mockServerOne = null, mockServerTwo = null;
        try {
            // then
            exception.expect(RuntimeException.class);
            exception.expectMessage(Matchers.containsString("Exception while binding MockServer to port"));

            // when - server started
            mockServerOne = new MockServer(MOCK_SERVER_PORT);

            // and - server started again on same port
            mockServerTwo = new MockServer(MOCK_SERVER_PORT);

        } finally {
            if (mockServerOne != null) {
                mockServerOne.stop();
            }
            if (mockServerTwo != null) {
                mockServerTwo.stop();
            }
        }
    }
}
