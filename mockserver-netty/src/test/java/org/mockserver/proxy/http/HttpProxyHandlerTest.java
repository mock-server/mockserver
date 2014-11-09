package org.mockserver.proxy.http;

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
import org.mockserver.client.serialization.VerificationChainSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.mappers.MockServerToNettyResponseMapper;
import org.mockserver.mappers.NettyToMockServerRequestMapper;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpCallback;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.filters.LogFilter;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationChain;

import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpProxyHandlerTest {


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
    @Mock
    private Verification mockVerification;
    @Mock
    private VerificationChain mockVerificationChain;
    // mockserver
    private LogFilter mockLogFilter;
    private HttpProxy mockHttpProxy;
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
    @Mock
    private VerificationSerializer mockVerificationSerializer;
    @Mock
    private VerificationChainSerializer mockVerificationChainSerializer;
    // netty
    @Mock
    private ChannelHandlerContext mockChannelHandlerContext;

    @InjectMocks
    private HttpProxyHandler httpProxyHandler;

    private DefaultFullHttpRequest createNettyHttpRequest(String uri, HttpMethod method, String some_content) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, Unpooled.copiedBuffer(some_content.getBytes()));
    }

    @Before
    public void setupFixture() {
        // given - a mock server handler
        mockLogFilter = mock(LogFilter.class);
        mockHttpProxy = mock(HttpProxy.class);
        httpProxyHandler = new HttpProxyHandler(mockLogFilter, mockHttpProxy, new InetSocketAddress(1234), false);

        initMocks(this);

        // given - serializers
        when(mockExpectationSerializer.deserialize(anyString())).thenReturn(mockExpectation);
        when(mockHttpRequestSerializer.deserialize(anyString())).thenReturn(mockHttpRequest);
        when(mockVerificationSerializer.deserialize(anyString())).thenReturn(mockVerification);
        when(mockVerificationChainSerializer.deserialize(anyString())).thenReturn(mockVerificationChain);

        // given - an expectation that can be setup
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
    public void shouldResetExpectations() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/reset", HttpMethod.PUT, "some_content");

        // when
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - filter and matcher is reset
        verify(mockLogFilter).reset();

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
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - filter and matcher is cleared
        verify(mockLogFilter).clear(mockHttpRequest);

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
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - expectations dumped to log
        verify(mockLogFilter).dumpToLog(mockHttpRequest, false);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.ACCEPTED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }

    @Test
    public void shouldDumpExpectationsToLogAsJava() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/dumpToLog?type=java", HttpMethod.PUT, "some_content");

        // when
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - expectations dumped to log
        verify(mockLogFilter).dumpToLog(mockHttpRequest, true);

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
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

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
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.BAD_REQUEST));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }

    @Test
    public void shouldVerifyPassingRequest() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/verify", HttpMethod.PUT, "some_content");
        when(mockLogFilter.verify(any(Verification.class))).thenReturn("");

        // when
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockVerificationSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockLogFilter).verify(mockVerification);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.ACCEPTED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }

    @Test
    public void shouldVerifyFailingRequest() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/verify", HttpMethod.PUT, "some_content");
        when(mockLogFilter.verify(any(Verification.class))).thenReturn("failure response");

        // when
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockVerificationSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockLogFilter).verify(mockVerification);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.NOT_ACCEPTABLE));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().array(), is("failure response".getBytes()));
    }

    @Test
    public void shouldVerifyChainPassingRequest() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/verifyChain", HttpMethod.PUT, "some_content");
        when(mockLogFilter.verify(any(VerificationChain.class))).thenReturn("");

        // when
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockVerificationChainSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockLogFilter).verify(mockVerificationChain);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.ACCEPTED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }

    @Test
    public void shouldVerifyChainFailingRequest() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/verifyChain", HttpMethod.PUT, "some_content");
        when(mockLogFilter.verify(any(VerificationChain.class))).thenReturn("failure response");

        // when
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - request deserialized
        verify(mockVerificationChainSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockLogFilter).verify(mockVerificationChain);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.NOT_ACCEPTABLE));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().array(), is("failure response".getBytes()));
    }

    @Test
    public void shouldStopMockServer() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = createNettyHttpRequest("/stop", HttpMethod.PUT, "some_content");

        // when
        httpProxyHandler.channelRead0(mockChannelHandlerContext, nettyHttpRequest);

        // then - mock server is stopped
        verify(mockHttpProxy).stop();

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<DefaultFullHttpResponse> responseCaptor = ArgumentCaptor.forClass(DefaultFullHttpResponse.class);
        verify(mockChannelHandlerContext).write(responseCaptor.capture());
        DefaultFullHttpResponse defaultFullHttpResponse = responseCaptor.getValue();
        assertThat(defaultFullHttpResponse.getStatus(), is(HttpResponseStatus.ACCEPTED));
        assertThat(defaultFullHttpResponse.getProtocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(defaultFullHttpResponse.content().readableBytes(), is(0));
    }
}
