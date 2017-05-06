package org.mockserver.mockserver;

import org.junit.Test;
import org.mockserver.model.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerHandlerConnectionOptionsTest extends MockServerHandlerTest {

    @Test
    public void shouldSuppressHeadersViaConnectionOptions() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request.withKeepAlive(true)))
                .thenReturn(
                        response()
                                .withBody("some_content")
                                .withConnectionOptions(
                                        new ConnectionOptions()
                                                .withSuppressContentLengthHeader(true)
                                                .withSuppressConnectionHeader(true)
                                )

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(embeddedChannel.isOpen(), is(true));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), empty());
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldOverrideContentLengthViaConnectionOptions() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request.withKeepAlive(true)))
                .thenReturn(
                        response()
                                .withBody("some_content")
                                .withConnectionOptions(
                                        new ConnectionOptions()
                                                .withContentLengthHeaderOverride(50)
                                )

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(embeddedChannel.isOpen(), is(true));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), containsInAnyOrder("keep-alive"));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldForceKeepAliveViaConnectionOptions() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request.withKeepAlive(false)))
                .thenReturn(
                        response()
                                .withBody("some_content")
                                .withConnectionOptions(
                                        new ConnectionOptions()
                                                .withKeepAliveOverride(true)
                                )

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(embeddedChannel.isOpen(), is(false));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), containsInAnyOrder("keep-alive"));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldSetKeepAliveViaIncomingRequest() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request.withKeepAlive(true)))
                .thenReturn(
                        response()
                                .withBody("some_content")

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        assertThat(embeddedChannel.isOpen(), is(true));
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), containsInAnyOrder("keep-alive"));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldForceNoKeepAliveViaConnectionOptions() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request.withKeepAlive(true)))
                .thenReturn(
                        response()
                                .withBody("some_content")
                                .withConnectionOptions(
                                        new ConnectionOptions()
                                                .withKeepAliveOverride(false)
                                )

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(embeddedChannel.isOpen(), is(true));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), containsInAnyOrder("close"));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldSetNoKeepAliveViaIncomingRequest() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request.withKeepAlive(false)))
                .thenReturn(
                        response()
                                .withBody("some_content")

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        assertThat(embeddedChannel.isOpen(), is(false));
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), containsInAnyOrder("close"));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldForceSocketClosedViaConnectionOptions() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request.withKeepAlive(true)))
                .thenReturn(
                        response()
                                .withBody("some_content")
                                .withConnectionOptions(
                                        new ConnectionOptions()
                                                .withCloseSocket(true)
                                )

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(embeddedChannel.isOpen(), is(false));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), containsInAnyOrder("close"));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldForceSocketOpenViaConnectionOptions() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request.withKeepAlive(false)))
                .thenReturn(
                        response()
                                .withBody("some_content")
                                .withConnectionOptions(
                                        new ConnectionOptions()
                                                .withCloseSocket(false)
                                )

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(embeddedChannel.isOpen(), is(true));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), containsInAnyOrder("close"));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

}
