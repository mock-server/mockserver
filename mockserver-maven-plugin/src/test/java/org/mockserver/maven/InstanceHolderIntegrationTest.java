package org.mockserver.maven;

import org.junit.Test;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.initialize.ExpectationInitializer;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;

/**
 * @author jamesdbloom
 */
public class InstanceHolderIntegrationTest {

    @Test
    public void shouldStartAndShutdownMockServer() {
        // given
        InstanceHolder instanceHolder = new InstanceHolder();
        int mockServerPort = PortFactory.findFreePort();
        MockServerClient client = new MockServerClient("127.0.0.1", mockServerPort);

        // when
        instanceHolder.start(mockServerPort, -1, null);

        // then
        assertThat(client.isRunning(), is(true));

        // and when
        instanceHolder.stop();

        // then
        assertThat(client.isRunning(), is(false));
    }

    @Test
    public void shouldRunInitializer() {
        // given
        InstanceHolder instanceHolder = new InstanceHolder();
        try {
            int mockServerPort = PortFactory.findFreePort();
            NettyHttpClient client = new NettyHttpClient();
            //

            // when
            instanceHolder.start(mockServerPort, -1, new ExpectationInitializer() {
                @Override
                public void initializeExpectations(MockServerClient mockServerClient) {
                    mockServerClient.when(request().withPath("/some_path")).respond(response().withBody("some_body"));
                }
            });

            // then
            assertThat(client.sendRequest(
                    outboundRequest("127.0.0.1", mockServerPort, "", request().withPath("/some_path"))
            ).getBody(), is(
                    response().withBody("some_body").getBody()
            ));
        } finally {
            // and when
            instanceHolder.stop();
        }

    }

    @Test
    public void shouldStartAndShutdownProxy() {
        // given
        InstanceHolder instanceHolder = new InstanceHolder();
        int proxyPort = PortFactory.findFreePort();
        ProxyClient client = new ProxyClient("127.0.0.1", proxyPort);

        // when
        instanceHolder.start(-1, proxyPort, null);

        // then
        assertThat(client.isRunning(), is(true));

        // and when
        instanceHolder.stop();

        // then
        assertThat(client.isRunning(), is(false));
    }
}
