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
import org.mockserver.matchers.TimeToLive;
import org.mockserver.model.HttpRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.TimeToLive.unlimited;
import static org.mockserver.matchers.Times.once;
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
    public void shouldClearLogsAndExpectationsForNullRequestMatcher() {
        // given
        HttpRequest request = request();

        // when
        httpStateHandler.clear(request);

        // then
        verify(mockMockServerMatcher).clear(null);
        verify(mockRequestResponseLogFilter).clear(null);
        verify(mockRequestLogFilter).clear(null);
        verify(mockLogFormatter).infoLog("clearing expectations and request logs that match:{}", (Object) null);
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
    public void shouldDumpRecordedRequestResponsesToLogAsJavaFormatParameter() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withQueryStringParameter("format", "java")
                .withBody(httpRequestSerializer.serialize(requestMatcher));

        // when
        httpStateHandler.dumpRecordedRequestResponsesToLog(request);

        // then
        verify(mockRequestResponseLogFilter).dumpToLog(requestMatcher, true);
        verify(mockLogFormatter).infoLog("dumped all requests and responses to the log in java that match:{}", requestMatcher);
    }

    @Test
    public void shouldDumpRecordedRequestResponsesToLogAsJavaTypeParameter() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withQueryStringParameter("type", "java")
                .withBody(httpRequestSerializer.serialize(requestMatcher));

        // when
        httpStateHandler.dumpRecordedRequestResponsesToLog(request);

        // then
        verify(mockRequestResponseLogFilter).dumpToLog(requestMatcher, true);
        verify(mockLogFormatter).infoLog("dumped all requests and responses to the log in java that match:{}", requestMatcher);
    }

    @Test
    public void shouldDumpRecordedRequestResponsesToLogAsJson() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withBody(httpRequestSerializer.serialize(requestMatcher));

        // when
        httpStateHandler.dumpRecordedRequestResponsesToLog(request);

        // then
        verify(mockRequestResponseLogFilter).dumpToLog(requestMatcher, false);
        verify(mockLogFormatter).infoLog("dumped all requests and responses to the log in json that match:{}", requestMatcher);
    }

    @Test
    public void shouldDumpRecordedRequestResponsesToLogAsJsonForNullRequestMatcher() {
        // given
        HttpRequest request = request();

        // when
        httpStateHandler.dumpRecordedRequestResponsesToLog(request);

        // then
        verify(mockRequestResponseLogFilter).dumpToLog(null, false);
        verify(mockLogFormatter).infoLog("dumped all requests and responses to the log in json that match:{}", null);
    }

    @Test
    public void shouldDumpExpectationsToLogAsJavaFormatParameter() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withQueryStringParameter("format", "java")
                .withBody(httpRequestSerializer.serialize(requestMatcher));

        // when
        httpStateHandler.dumpExpectationsToLog(request);

        // then
        verify(mockMockServerMatcher).dumpToLog(requestMatcher, true);
        verify(mockLogFormatter).infoLog("dumped all active expectations to the log in java that match:{}", requestMatcher);
    }

    @Test
    public void shouldDumpExpectationsToLogAsJavaTypeParameter() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withQueryStringParameter("type", "java")
                .withBody(httpRequestSerializer.serialize(requestMatcher));

        // when
        httpStateHandler.dumpExpectationsToLog(request);

        // then
        verify(mockMockServerMatcher).dumpToLog(requestMatcher, true);
        verify(mockLogFormatter).infoLog("dumped all active expectations to the log in java that match:{}", requestMatcher);
    }

    @Test
    public void shouldDumpExpectationsToLogAsJson() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withBody(httpRequestSerializer.serialize(requestMatcher));

        // when
        httpStateHandler.dumpExpectationsToLog(request);

        // then
        verify(mockMockServerMatcher).dumpToLog(requestMatcher, false);
        verify(mockLogFormatter).infoLog("dumped all active expectations to the log in json that match:{}", requestMatcher);
    }

    @Test
    public void shouldRetrieveExpectationsAsJson() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withQueryStringParameter("type", "expectation")
                .withBody(httpRequestSerializer.serialize(requestMatcher));
        List<Expectation> expectations = Collections.singletonList(new Expectation(requestMatcher, once(), unlimited()));
        when(mockMockServerMatcher.retrieveExpectations(requestMatcher)).thenReturn(expectations);

        // when
        String retrieve = httpStateHandler.retrieve(request);

        // then
        assertThat(retrieve, is("[ {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"body\" : \"some_body\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 1," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]"));
        verify(mockLogFormatter).infoLog("retrieving expectations that match:{}", requestMatcher);
    }

    @Test
    public void shouldRetrieveExpectationsAsJava() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withQueryStringParameter("type", "expectation")
                .withQueryStringParameter("format", "java")
                .withBody(httpRequestSerializer.serialize(requestMatcher));
        List<Expectation> expectations = Collections.singletonList(new Expectation(requestMatcher, once(), unlimited()));
        when(mockMockServerMatcher.retrieveExpectations(requestMatcher)).thenReturn(expectations);

        // when
        String retrieve = httpStateHandler.retrieve(request);

        // then
        assertThat(retrieve, is(NEW_LINE +
                "new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                ".when(" + NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withBody(new StringBody(\"some_body\"))," + NEW_LINE +
                "        Times.once()" + NEW_LINE +
                ");" + NEW_LINE +
                NEW_LINE));
        verify(mockLogFormatter).infoLog("retrieving expectations that match:{}", requestMatcher);
    }

    @Test
    public void shouldRetrieveRequestsAsJson() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withBody(httpRequestSerializer.serialize(requestMatcher));
        HttpRequest[] httpRequests = new HttpRequest[]{
                request("some_path_one"),
                request("some_path_two")
        };
        when(mockRequestLogFilter.retrieve(requestMatcher)).thenReturn(httpRequests);

        // when
        String retrieve = httpStateHandler.retrieve(request);

        // then
        assertThat(retrieve, is("[ {" + NEW_LINE +
                "  \"path\" : \"some_path_one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"some_path_two\"" + NEW_LINE +
                "} ]"));
        verify(mockLogFormatter).infoLog("retrieving requests that match:{}", requestMatcher);
    }

    @Test
    public void shouldRetrieveRequestsAsJava() {
        // given
        HttpRequest requestMatcher = request().withBody("some_body");
        HttpRequest request = request()
                .withQueryStringParameter("format", "java")
                .withBody(httpRequestSerializer.serialize(requestMatcher));
        HttpRequest[] httpRequests = new HttpRequest[]{
                request("some_path_one"),
                request("some_path_two")
        };
        when(mockRequestLogFilter.retrieve(requestMatcher)).thenReturn(httpRequests);

        // when
        String retrieve = httpStateHandler.retrieve(request);

        // then
        assertThat(retrieve, is(NEW_LINE +
                "request()" + NEW_LINE +
                "        .withPath(\"some_path_one\")" + NEW_LINE +
                NEW_LINE +
                NEW_LINE +
                "request()" + NEW_LINE +
                "        .withPath(\"some_path_two\")" + NEW_LINE +
                NEW_LINE));
        verify(mockLogFormatter).infoLog("retrieving requests that match:{}", requestMatcher);
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