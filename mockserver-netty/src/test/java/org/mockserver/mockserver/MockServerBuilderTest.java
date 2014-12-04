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

        // when
        MockServer mockServer = new MockServerBuilder().withHTTPPort(port).build();

        try {
            // then
            assertThat(mockServer.getPort(), is(port));
        } finally {
            mockServer.stop();
        }
    }
    
}
