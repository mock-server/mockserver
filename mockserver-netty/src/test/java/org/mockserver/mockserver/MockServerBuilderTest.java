package org.mockserver.mockserver;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author jamesdbloom
 */
public class MockServerBuilderTest {

    @Test
    public void shouldConfigureHTTPPort() {
        // given
        // - some ports
        Integer port = new Random().nextInt();
        Integer securePort = null;
        // - a build and http proxy
        MockServerBuilder mockServerBuilder = spy(new MockServerBuilder());
        MockServer mockServer = mock(MockServer.class);
        when(mockServerBuilder.newMockServer()).thenReturn(mockServer);

        // when
        MockServer actual = mockServerBuilder.withHTTPPort(port).build();

        // then
        assertEquals(mockServer, actual);
        verify(mockServer).start(
                port,
                securePort
        );
    }

    @Test
    public void shouldConfigureHTTPSPort() {
        // given
        // - some ports
        Integer port = null;
        Integer securePort = new Random().nextInt();
        // - a build and http proxy
        MockServerBuilder mockServerBuilder = spy(new MockServerBuilder());
        MockServer mockServer = mock(MockServer.class);
        when(mockServerBuilder.newMockServer()).thenReturn(mockServer);

        // when
        MockServer actual = mockServerBuilder.withHTTPSPort(securePort).build();

        // then
        assertEquals(mockServer, actual);
        verify(mockServer).start(
                port,
                securePort
        );
    }

    @Test
    public void shouldReturnCorrectObject() {
        MockServer mockServer = new MockServerBuilder().withHTTPPort(9090).build();
        assertTrue(mockServer instanceof MockServer);
        mockServer.stop();
        Thread thread = new MockServerBuilder().withHTTPPort(9090).buildAndReturnThread();
        assertTrue(thread instanceof Thread);
        thread.stop();
    }
}
