package org.mockserver.mockserver;

import org.junit.Test;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

/**
 * @author jamesdbloom
 */
public class MockServerBuilderTest {

    @Test
    public void shouldConfigureAllPorts() {
        // given
        Integer port = PortFactory.findFreePort();

        // when
        MockServer mockServer = new MockServerBuilder().withHTTPPort(port).build();

        try {
            // then
            assertThat(mockServer.getPort(), is(port));
        } finally {
            mockServer.stop();
        }
    }

    @Test
    public void testPortZero() {
        // when
        MockServer mockServer = new MockServerBuilder().withHTTPPort(0).build();

        try {
            // then
            assertThat(mockServer.getPort(), not(0));
        } finally {
            mockServer.stop();
        }
    }
    
}
