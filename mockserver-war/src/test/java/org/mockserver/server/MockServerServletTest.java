package org.mockserver.server;

import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.*;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerServletTest {

    // model objects
    @Mock
    Expectation mockExpectation;
    @Mock
    HttpRequest mockHttpRequest;
    @Mock
    HttpResponse mockHttpResponse;
    @Mock
    HttpTemplate mockHttpTemplate;
    @Mock
    HttpForward mockHttpForward;
    @Mock
    HttpError mockHttpError;
    @Mock
    HttpClassCallback mockHttpClassCallback;
    @Mock
    Verification mockVerification;
    @Mock
    VerificationSequence mockVerificationSequence;
    // mockserver
    @Mock
    MockServerMatcher mockMockServerMatcher;
    @Mock
    RequestLogFilter mockRequestLogFilter;
    @Mock
    HttpStateHandler httpStateHandler;
    @Mock
    ActionHandler mockActionHandler;
    // decoders
    @Mock
    HttpServletRequestToMockServerRequestDecoder mockHttpServletRequestToMockServerRequestDecoder;
    @Spy
    MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder = new MockServerResponseToHttpServletResponseEncoder();
    // serializers
    @Mock
    ExpectationSerializer mockExpectationSerializer;
    @Mock
    VerificationSerializer mockVerificationSerializer;
    @Mock
    VerificationSequenceSerializer mockVerificationSequenceSerializer;

    @InjectMocks
    MockServerServlet mockServerServlet;

    @Before
    public void setupTestFixture() {
        mockServerServlet = new MockServerServlet();

        initMocks(this);

        // given - serializers
        when(mockExpectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{mockExpectation});
        when(mockVerificationSerializer.deserialize(anyString())).thenReturn(mockVerification);
        when(mockVerificationSequenceSerializer.deserialize(anyString())).thenReturn(mockVerificationSequence);

        // given - an expectation that can be setup
        when(mockMockServerMatcher.when(any(HttpRequest.class), any(Times.class), any(TimeToLive.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenRespond(any(HttpResponse.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenRespond(any(HttpTemplate.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenForward(any(HttpForward.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenError(any(HttpError.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenCallback(any(HttpClassCallback.class))).thenReturn(mockExpectation);

        // given - an expectation that has been setup
        when(mockExpectation.getHttpRequest()).thenReturn(mockHttpRequest);
        when(mockExpectation.getTimes()).thenReturn(Times.once());
        when(mockExpectation.getHttpResponse()).thenReturn(mockHttpResponse);
        when(mockExpectation.getHttpResponseTemplate()).thenReturn(mockHttpTemplate);
        when(mockExpectation.getHttpForward()).thenReturn(mockHttpForward);
        when(mockExpectation.getHttpError()).thenReturn(mockHttpError);
        when(mockExpectation.getHttpClassCallback()).thenReturn(mockHttpClassCallback);
    }

}
