package org.mockserver.mockserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.*;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerHandlerTest {

    // model objects
    @Mock
    Expectation mockExpectation;
    @Mock
    HttpRequest mockHttpRequest;
    @Mock
    HttpResponse mockHttpResponse;
    @Mock
    HttpForward mockHttpForward;
    @Mock
    HttpError mockHttpError;
    @Mock
    HttpCallback mockHttpCallback;
    @Mock
    Verification mockVerification;
    @Mock
    VerificationSequence mockVerificationSequence;
    // mockserver
    RequestLogFilter mockRequestLogFilter;
    MockServerMatcher mockMockServerMatcher;
    MockServer mockMockServer;
    @Mock
    ActionHandler mockActionHandler;
    // serializers
    @Mock
    ExpectationSerializer mockExpectationSerializer;
    @Mock
    HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    VerificationSerializer mockVerificationSerializer;
    @Mock
    VerificationSequenceSerializer mockVerificationSequenceSerializer;
    // netty
    @Mock
    ChannelHandlerContext mockChannelHandlerContext;

    @InjectMocks
    MockServerHandler mockServerHandler;
    EmbeddedChannel embeddedChannel;

    @Before
    public void setupFixture() {
        // given - a mock server handler
        mockRequestLogFilter = mock(RequestLogFilter.class);
        mockMockServerMatcher = mock(MockServerMatcher.class);
        mockMockServer = mock(MockServer.class);
        mockServerHandler = new MockServerHandler(mockMockServer, mockMockServerMatcher, mockRequestLogFilter);
        embeddedChannel = new EmbeddedChannel(mockServerHandler);

        initMocks(this);

        // given - serializers
        when(mockExpectationSerializer.deserialize(anyString())).thenReturn(mockExpectation);
        when(mockHttpRequestSerializer.deserialize(anyString())).thenReturn(mockHttpRequest);
        when(mockVerificationSerializer.deserialize(anyString())).thenReturn(mockVerification);
        when(mockVerificationSequenceSerializer.deserialize(anyString())).thenReturn(mockVerificationSequence);

        // given - an expectation that can be setup
        when(mockMockServerMatcher.when(any(HttpRequest.class), any(Times.class), any(TimeToLive.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenRespond(any(HttpResponse.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenForward(any(HttpForward.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenError(any(HttpError.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenCallback(any(HttpCallback.class))).thenReturn(mockExpectation);

        // given - an expectation that has been setup
        when(mockExpectation.getHttpRequest()).thenReturn(mockHttpRequest);
        when(mockExpectation.getTimes()).thenReturn(Times.once());
        when(mockExpectation.getHttpResponse(anyBoolean())).thenReturn(mockHttpResponse);
        when(mockExpectation.getHttpForward()).thenReturn(mockHttpForward);
        when(mockExpectation.getHttpError()).thenReturn(mockHttpError);
        when(mockExpectation.getHttpCallback()).thenReturn(mockHttpCallback);
    }

    @After
    public void closeEmbeddedChanel() {
        assertThat(embeddedChannel.finish(), is(false));
    }
}
