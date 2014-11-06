package org.mockserver.mockserver;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.MockServerToNettyResponseMapper;
import org.mockserver.mappers.NettyToMockServerRequestMapper;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.*;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.filters.LogFilter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerHandlerTest {


    // model objects
    @Mock
    private Expectation mockExpectation;
    @Mock
    private HttpRequest mockHttpRequest;
    @Mock
    private HttpResponse mockHttpResponse;
    @Mock
    private HttpForward mockHttpForward;
    @Mock
    private HttpCallback mockHttpCallback;
    // mockserver
    private LogFilter mockLogFilter;
    private MockServerMatcher mockMockServerMatcher;
    @Mock
    private MockServer mockMockServer;
    @Mock
    private ActionHandler mockActionHandler;
    // mappers
    @Mock
    private NettyToMockServerRequestMapper mockNettyToMockServerRequestMapper;
    @Mock
    private MockServerToNettyResponseMapper mockMockServerToNettyResponseMapper;
    // serializers
    @Mock
    private ExpectationSerializer mockExpectationSerializer;
    @Mock
    private HttpRequestSerializer mockHttpRequestSerializer;
    // netty
    @Mock
    private ChannelHandlerContext mockChannelHandlerContext;

    @InjectMocks
    private MockServerHandler mockServerHandler;

    private DefaultFullHttpRequest createNettyHttpRequest(String uri, HttpMethod method, String some_content) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, Unpooled.copiedBuffer(some_content.getBytes()));
    }

    @Before
    public void setupFixture() {
        // given - a mock server handler
        mockMockServerMatcher = mock(MockServerMatcher.class);
        mockLogFilter = mock(LogFilter.class);
        mockServerHandler = new MockServerHandler(mockMockServerMatcher, mockLogFilter, mockMockServer, false);

        initMocks(this);

        // given - serializers
        when(mockExpectationSerializer.deserialize(anyString())).thenReturn(mockExpectation);
        when(mockHttpRequestSerializer.deserialize(anyString())).thenReturn(mockHttpRequest);

        // given - an expectation that can be setup
        when(mockMockServerMatcher.when(any(HttpRequest.class), any(Times.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenRespond(any(HttpResponse.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenForward(any(HttpForward.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenCallback(any(HttpCallback.class))).thenReturn(mockExpectation);

        // given - an expectation that has been setup
        when(mockExpectation.getHttpRequest()).thenReturn(mockHttpRequest);
        when(mockExpectation.getTimes()).thenReturn(Times.once());
        when(mockExpectation.getHttpResponse(anyBoolean())).thenReturn(mockHttpResponse);
        when(mockExpectation.getHttpForward()).thenReturn(mockHttpForward);
        when(mockExpectation.getHttpCallback()).thenReturn(mockHttpCallback);
    }

    @Test
    public void shouldSetupExpectation() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/expectation", HttpMethod.PUT, "some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockExpectationSerializer).deserialize("some_content");

        // and - expectation correctly setup
        verify(mockMockServerMatcher).when(any(HttpRequest.class), any(Times.class));
        verify(mockExpectation).thenRespond(any(HttpResponse.class));
        verify(mockExpectation).thenForward(any(HttpForward.class));
        verify(mockExpectation).thenCallback(any(HttpCallback.class));

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.CREATED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }

    @Test
    public void shouldResetExpectations() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/reset", HttpMethod.PUT, "some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - filter and matcher is reset
        verify(mockLogFilter).reset();
        verify(mockMockServerMatcher).reset();

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.ACCEPTED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }

    @Test
    public void shouldClearExpectations() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/clear", HttpMethod.PUT, "some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - filter and matcher is cleared
        verify(mockLogFilter).clear(mockHttpRequest);
        verify(mockMockServerMatcher).clear(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.ACCEPTED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }

    @Test
    public void shouldDumpExpectationsToLog() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/dumpToLog", HttpMethod.PUT, "some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - expectations dumped to log
        verify(mockMockServerMatcher).dumpToLog(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.ACCEPTED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }

    @Test
    public void shouldReturnRecordedRequests() {
        // given
        Expectation[] expectations = {};
        when(mockLogFilter.retrieve(mockHttpRequest)).thenReturn(expectations);
        when(mockExpectationSerializer.serialize(expectations)).thenReturn("expectations");
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/retrieve", HttpMethod.PUT, "some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - expectations dumped to log
        verify(mockLogFilter).retrieve(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.OK));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().array(), is("expectations".getBytes()));
    }

    @Test
    public void shouldReturnNotFoundAfterException() {
        // given - a mapper that throws an exception
        when(mockNettyToMockServerRequestMapper.mapNettyRequestToMockServerRequest(any(FullHttpRequest.class), anyBoolean())).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // and - a request
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/randomPath", HttpMethod.GET, "some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.BAD_REQUEST));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }

    @Test
    public void shouldActionResult() {
        // given - a mapper
        when(mockNettyToMockServerRequestMapper.mapNettyRequestToMockServerRequest(any(FullHttpRequest.class), anyBoolean())).thenReturn(mockHttpRequest);

        // and - a handler returning an action
        when(mockMockServerMatcher.handle(mockHttpRequest)).thenReturn(response().withBody("some_response"));
        when(mockActionHandler.processAction(response().withBody("some_response"), mockHttpRequest)).thenReturn(mockHttpResponse);

        // and - a response mapper
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PAYMENT_REQUIRED, Unpooled.copiedBuffer("some_content".getBytes()));
        when(mockMockServerToNettyResponseMapper.mapMockServerResponseToNettyResponse(any(HttpResponse.class))).thenReturn(httpResponse);


        // and - a request
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/randomPath", HttpMethod.GET, "some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.PAYMENT_REQUIRED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().array(), is("some_content".getBytes()));
    }

    @Test
    public void shouldStopMockServer() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/stop", HttpMethod.PUT, "some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - mock server is stopped
        verify(mockMockServer).stop();

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.ACCEPTED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }
}
