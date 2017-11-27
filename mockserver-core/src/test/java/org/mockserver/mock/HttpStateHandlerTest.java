package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.client.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.logging.LogFormatter;
import org.mockserver.model.HttpRequest;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class HttpStateHandlerTest {

    @Mock
    private LogFormatter mockLogFormatter;
    // mockserver
    private RequestLogFilter mockRequestLogFilter;
    private RequestResponseLogFilter mockRequestResponseLogFilter;
    private MockServerMatcher mockMockServerMatcher;
    // serializers
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestToJavaSerializer httpRequestToJavaSerializer = new HttpRequestToJavaSerializer();
    private ExpectationToJavaSerializer expectationToJavaSerializer = new ExpectationToJavaSerializer();
    @InjectMocks
    private HttpStateHandler httpStateHandler;

    @Before
    public void prepareTestFixture() {
        mockRequestLogFilter = mock(RequestLogFilter.class);
        mockRequestResponseLogFilter = mock(RequestResponseLogFilter.class);
        mockMockServerMatcher = mock(MockServerMatcher.class);
        httpStateHandler = new HttpStateHandler(mockRequestLogFilter, mockRequestResponseLogFilter, mockMockServerMatcher);
        initMocks(this);
    }

    @Test
    public void shouldClearLogsAndExpectations() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request().withBody(httpRequestSerializer.serialize(requestMatcher));

        // when
        httpStateHandler.clear(request);

        // then
        verify(mockMockServerMatcher).clear(requestMatcher);
        verify(mockRequestResponseLogFilter).clear(requestMatcher);
        verify(mockRequestLogFilter).clear(requestMatcher);
        verify(mockLogFormatter).infoLog("clearing expectations and request logs that match:{}", requestMatcher);
    }

    @Test
    public void shouldClearLogsOnly() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withQueryStringParameter("type", "log")
                .withBody(httpRequestSerializer.serialize(requestMatcher));

        // when
        httpStateHandler.clear(request);

        // then
        verifyZeroInteractions(mockMockServerMatcher);
        verifyZeroInteractions(mockRequestResponseLogFilter);
        verify(mockRequestLogFilter).clear(requestMatcher);
        verify(mockLogFormatter).infoLog("clearing request logs that match:{}", requestMatcher);
    }

    @Test
    public void shouldClearExpectationsOnly() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withQueryStringParameter("type", "expectation")
                .withBody(httpRequestSerializer.serialize(requestMatcher));

        // when
        httpStateHandler.clear(request);

        // then
        verify(mockMockServerMatcher).clear(requestMatcher);
        verify(mockRequestResponseLogFilter).clear(requestMatcher);
        verifyZeroInteractions(mockRequestLogFilter);
        verify(mockLogFormatter).infoLog("clearing expectations that match:{}", requestMatcher);
    }

    @Test
    public void shouldReset() {
        // when
        httpStateHandler.reset();

        // then
        verify(mockMockServerMatcher).reset();
        verify(mockRequestResponseLogFilter).reset();
        verify(mockRequestLogFilter).reset();
        verify(mockLogFormatter).infoLog("resetting all expectations and request logs");
    }

}