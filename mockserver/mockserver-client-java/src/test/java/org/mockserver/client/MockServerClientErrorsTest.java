package org.mockserver.client;

import org.junit.Test;
import org.mockserver.httpclient.SocketConnectionException;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerClientErrorsTest {

    @Test
    public void shouldHandleSocketErrorForReset() {
        // given
        int freePort = PortFactory.findFreePort();
        MockServerClient mockServerClient = new MockServerClient("localhost", freePort);

        // when
        SocketConnectionException clientException = assertThrows(SocketConnectionException.class, mockServerClient::reset);

        // then
        assertThat(clientException.getMessage(), equalTo("Unable to connect to socket localhost/127.0.0.1:" + freePort));
    }

    @Test
    public void shouldHandleSocketErrorForClear() {
        // given
        int freePort = PortFactory.findFreePort();
        MockServerClient mockServerClient = new MockServerClient("localhost", freePort);

        // when
        SocketConnectionException clientException = assertThrows(SocketConnectionException.class, () -> mockServerClient.clear(request()));

        // then
        assertThat(clientException.getMessage(), equalTo("Unable to connect to socket localhost/127.0.0.1:" + freePort));
    }

    @Test
    public void shouldHandleSocketErrorForExpectation() {
        // given
        int freePort = PortFactory.findFreePort();
        MockServerClient mockServerClient = new MockServerClient("localhost", freePort);

        // when
        SocketConnectionException clientException = assertThrows(SocketConnectionException.class, () -> mockServerClient.when(request()).respond(response()));

        // then
        assertThat(clientException.getMessage(), equalTo("Unable to connect to socket localhost/127.0.0.1:" + freePort));
    }

}
