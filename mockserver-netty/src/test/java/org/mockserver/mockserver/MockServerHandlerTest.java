package org.mockserver.mockserver;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.LogFilter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpCallback;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpRequest.request;
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
    @Mock
    private Verification mockVerification;
    @Mock
    private VerificationSequence mockVerificationSequence;
    // mockserver
    private LogFilter mockLogFilter;
    private MockServerMatcher mockMockServerMatcher;
    @Mock
    private MockServer mockMockServer;
    @Mock
    private ActionHandler mockActionHandler;
    // serializers
    @Mock
    private ExpectationSerializer mockExpectationSerializer;
    @Mock
    private HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    private VerificationSerializer mockVerificationSerializer;
    @Mock
    private VerificationSequenceSerializer mockVerificationSequenceSerializer;
    // netty
    @Mock
    private ChannelHandlerContext mockChannelHandlerContext;

    @InjectMocks
    private MockServerHandler mockServerHandler;

    @Before
    public void setupFixture() {
        // given - a mock server handler
        mockLogFilter = mock(LogFilter.class);
        mockMockServerMatcher = mock(MockServerMatcher.class);
        mockServerHandler = new MockServerHandler(mockMockServer, mockMockServerMatcher, mockLogFilter);

        initMocks(this);

        // given - channel handle context
        when(mockChannelHandlerContext.writeAndFlush(any(HttpResponse.class))).thenReturn(mock(ChannelFuture.class));
        when(mockChannelHandlerContext.write(any(HttpResponse.class))).thenReturn(mock(ChannelFuture.class));

        // given - serializers
        when(mockExpectationSerializer.deserialize(anyString())).thenReturn(mockExpectation);
        when(mockHttpRequestSerializer.deserialize(anyString())).thenReturn(mockHttpRequest);
        when(mockVerificationSerializer.deserialize(anyString())).thenReturn(mockVerification);
        when(mockVerificationSequenceSerializer.deserialize(anyString())).thenReturn(mockVerificationSequence);

        // given - an expectation that can be setup
        when(mockMockServerMatcher.when(any(HttpRequest.class), any(Times.class), any(TimeToLive.class))).thenReturn(mockExpectation);
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
        HttpRequest request = request("/expectation").withMethod("PUT").withBody("some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - request deserialized
        verify(mockExpectationSerializer).deserialize("some_content");

        // and - expectation correctly setup
        verify(mockMockServerMatcher).when(any(HttpRequest.class), any(Times.class), any(TimeToLive.class));
        verify(mockExpectation).thenRespond(any(HttpResponse.class));
        verify(mockExpectation).thenForward(any(HttpForward.class));
        verify(mockExpectation).thenCallback(any(HttpCallback.class));

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.CREATED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldResetExpectations() {
        // given
        HttpRequest request = request("/reset").withMethod("PUT");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - filter and matcher is reset
        verify(mockLogFilter).reset();
        verify(mockMockServerMatcher).reset();

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldClearExpectations() {
        // given
        HttpRequest request = request("/clear").withMethod("PUT").withBody("some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - filter and matcher is cleared
        verify(mockLogFilter).clear(mockHttpRequest);
        verify(mockMockServerMatcher).clear(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldDumpExpectationsToLog() {
        // given
        HttpRequest request = request("/dumpToLog").withMethod("PUT").withBody("some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - expectations dumped to log
        verify(mockMockServerMatcher).dumpToLog(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldReturnRecordedRequests() {
        // given
        Expectation[] expectations = {};
        when(mockLogFilter.retrieve(mockHttpRequest)).thenReturn(expectations);
        when(mockExpectationSerializer.serialize(expectations)).thenReturn("expectations");
        HttpRequest request = request("/retrieve").withMethod("PUT").withBody("some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - expectations dumped to log
        verify(mockLogFilter).retrieve(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.OK.code()));
        assertThat(httpResponse.getBodyAsString(), is("expectations"));
    }

    @Test
    public void shouldReturnBadRequestAfterException() {
        // given
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");
        when(mockMockServerMatcher.handle(request)).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.BAD_REQUEST.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldReturnNotFoundAfterNoMatch() {
        // given
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");
        when(mockMockServerMatcher.handle(request)).thenReturn(null);

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.NOT_FOUND.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldActionResult() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.handle(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request))
                .thenReturn(
                        response()
                                .withStatusCode(HttpResponseStatus.PAYMENT_REQUIRED.code())
                                .withBody("some_content")

                );

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.PAYMENT_REQUIRED.code()));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldVerifyPassingRequest() {
        // given
        when(mockLogFilter.verify(any(Verification.class))).thenReturn("");

        // and - a request
        HttpRequest request = request("/verify").withMethod("PUT").withBody("some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - request deserialized
        verify(mockVerificationSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockLogFilter).verify(mockVerification);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldVerifyFailingRequest() {
        // given
        when(mockLogFilter.verify(any(Verification.class))).thenReturn("failure response");

        // and - a request
        HttpRequest request = request("/verify").withMethod("PUT").withBody("some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - request deserialized
        verify(mockVerificationSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockLogFilter).verify(mockVerification);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.NOT_ACCEPTABLE.code()));
        assertThat(httpResponse.getBodyAsString(), is("failure response"));
    }

    @Test
    public void shouldVerifySequencePassingRequest() {
        // given
        when(mockLogFilter.verify(any(VerificationSequence.class))).thenReturn("");

        // and - a request
        HttpRequest request = request("/verifySequence").withMethod("PUT").withBody("some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - request deserialized
        verify(mockVerificationSequenceSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockLogFilter).verify(mockVerificationSequence);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldVerifySequenceFailingRequest() {
        // given
        when(mockLogFilter.verify(any(VerificationSequence.class))).thenReturn("failure response");

        // and - a request
        HttpRequest request = request("/verifySequence").withMethod("PUT").withBody("some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - request deserialized
        verify(mockVerificationSequenceSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockLogFilter).verify(mockVerificationSequence);

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.NOT_ACCEPTABLE.code()));
        assertThat(httpResponse.getBodyAsString(), is("failure response"));
    }

    @Test
    public void shouldStopMockServer() {
        // given
        HttpRequest request = request("/stop").withMethod("PUT").withBody("some_content");

        // when
        mockServerHandler.channelRead0(mockChannelHandlerContext, request);

        // then - mock server is stopped
        verify(mockMockServer).stop();

        // and - correct response written to ChannelHandlerContext
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockChannelHandlerContext).writeAndFlush(responseCaptor.capture());
        HttpResponse httpResponse = responseCaptor.getValue();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }
}
