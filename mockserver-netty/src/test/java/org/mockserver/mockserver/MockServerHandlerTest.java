package org.mockserver.mockserver;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;

import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerHandlerTest extends ExpectationHandlerTest {

    @InjectMocks
    private MockServerHandler mockServerHandler;

    @Before
    public void setupFixture() {
        server = mock(MockServer.class);
        mockActionHandler = mock(ActionHandler.class);

        httpStateHandler = new HttpStateHandler();
        mockServerHandler = new MockServerHandler((MockServer) server, httpStateHandler, null);

        initMocks(this);

        embeddedChannel = new EmbeddedChannel(mockServerHandler);
    }

}
