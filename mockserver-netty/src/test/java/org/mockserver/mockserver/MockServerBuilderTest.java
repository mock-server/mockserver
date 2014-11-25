package org.mockserver.mockserver;

import org.junit.Test;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class MockServerBuilderTest {

    @Test
    public void shouldConfigureAllPorts() {
        // given
        Integer port = PortFactory.findFreePort();
        Integer securePort = PortFactory.findFreePort();

        // when
        MockServer mockServer = new MockServerBuilder().withHTTPPort(port).withHTTPSPort(securePort).build();

        try {
            // then
            assertThat(mockServer.getPort(), is(port));
            assertThat(mockServer.getSecurePort(), is(securePort));
        } finally {
            mockServer.stop();
        }
    }

    @Test
    public void shouldConfigureHTTPPort() {
        // given
        Integer port = PortFactory.findFreePort();
        Integer securePort = null;

        // when
        MockServer mockServer = new MockServerBuilder().withHTTPPort(port).build();

        try {
            // then
            assertThat(mockServer.getPort(), is(port));
            assertThat(mockServer.getSecurePort(), is(securePort));
        } finally {
            mockServer.stop();
        }
    }

    @Test
    public void shouldConfigureHTTPSPort() {
        // given
        Integer port = null;
        Integer securePort = PortFactory.findFreePort();

        // when
        MockServer mockServer = new MockServerBuilder().withHTTPSPort(securePort).build();

        try {
            // then
            assertThat(mockServer.getPort(), is(port));
            assertThat(mockServer.getSecurePort(), is(securePort));
        } finally {
            mockServer.stop();
        }
    }
}
