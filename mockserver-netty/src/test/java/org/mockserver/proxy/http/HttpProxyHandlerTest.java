package org.mockserver.proxy.http;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.mockserver.ExpectationHandlerTest;
import org.mockserver.proxy.Proxy;

import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpProxyHandlerTest extends ExpectationHandlerTest {

    @InjectMocks
    private HttpProxyHandler httpProxyHandler;

    @Before
    public void setupFixture() {
        server = mock(Proxy.class);
        mockActionHandler = mock(ActionHandler.class);

        httpStateHandler = new HttpStateHandler();
        httpProxyHandler = new HttpProxyHandler((Proxy) server, httpStateHandler);

        initMocks(this);

        embeddedChannel = new EmbeddedChannel(httpProxyHandler);
    }

}
