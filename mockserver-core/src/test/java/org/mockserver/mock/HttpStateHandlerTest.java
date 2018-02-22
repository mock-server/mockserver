package org.mockserver.mock;

import com.google.common.net.MediaType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.client.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.log.model.RequestLogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;

import java.util.Arrays;
import java.util.Collections;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpStateHandlerTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer(new MockServerLogger());
    private HttpRequestToJavaSerializer httpRequestToJavaSerializer = new HttpRequestToJavaSerializer();
    private ExpectationSerializer httpExpectationSerializer = new ExpectationSerializer(new MockServerLogger());
    private ExpectationToJavaSerializer httpExpectationToJavaSerializer = new ExpectationToJavaSerializer();
    private Scheduler scheduler;
    @Mock
    private MockServerLogger mockLogFormatter;
    @InjectMocks
    private HttpStateHandler httpStateHandler;

    @Before
    public void prepareTestFixture() {
        scheduler = mock(Scheduler.class);
        httpStateHandler = new HttpStateHandler(scheduler);
        initMocks(this);
    }

    @Test
    public void shouldClearLogsAndExpectationsForNullRequestMatcher() {
        // given - a request
        HttpRequest request = request();
        // given - some existing expectations
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        // given - some log entries
        httpStateHandler.log(new RequestLogEntry(request("request_one")));

        // when
        httpStateHandler.clear(request);

        // then - correct log entries removed
        assertThat(httpStateHandler.retrieve(request), is(response().withBody("[]", JSON_UTF_8).withStatusCode(200)));
        // then - correct expectations removed
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), nullValue());
        // then - activity logged
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationOne);
        verify(mockLogFormatter).info(CLEARED, (HttpRequest) null, "clearing expectations and request logs that match:{}", "{}");
    }

    @Test
    public void shouldClearLogsAndExpectations() {
        // given - a request
        HttpRequest request = request().withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some existing expectations
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond(response("response_two"));
        httpStateHandler.add(expectationTwo);
        // given - some log entries
        httpStateHandler.log(new RequestLogEntry(request("request_one")));
        httpStateHandler.log(new RequestLogEntry(request("request_two")));

        // when
        httpStateHandler.clear(request);

        // then - correct log entries removed
        assertThat(
            httpStateHandler.retrieve(request().withBody(httpRequestSerializer.serialize(request("request_one")))),
            is(response().withBody("[]", JSON_UTF_8).withStatusCode(200))
        );
        assertThat(
            httpStateHandler.retrieve(request().withBody(httpRequestSerializer.serialize(request("request_two")))),
            is(response().withBody(httpRequestSerializer.serialize(Collections.singletonList(
                request("request_two")
            )), JSON_UTF_8).withStatusCode(200))
        );
        // then - correct expectations removed
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), nullValue());
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_two")), is(expectationTwo));
        // then - activity logged
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationOne);
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_two"), "creating expectation:{}", expectationTwo);
        verify(mockLogFormatter).info(CLEARED, request("request_one"), "clearing expectations and request logs that match:{}", request("request_one"));
    }

    @Test
    public void shouldClearLogsOnly() {
        // given - a request
        HttpRequest request = request()
            .withQueryStringParameter("type", "log")
            .withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some existing expectations
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond(response("response_two"));
        httpStateHandler.add(expectationTwo);
        // given - some log entries
        httpStateHandler.log(new RequestLogEntry(request("request_one")));
        httpStateHandler.log(new RequestLogEntry(request("request_two")));

        // when
        httpStateHandler.clear(request);

        // then - correct log entries removed
        assertThat(
            httpStateHandler.retrieve(request().withBody(httpRequestSerializer.serialize(request("request_one")))),
            is(response().withBody("[]", JSON_UTF_8).withStatusCode(200))
        );
        assertThat(
            httpStateHandler.retrieve(request().withBody(httpRequestSerializer.serialize(request("request_two")))),
            is(response().withBody(httpRequestSerializer.serialize(Collections.singletonList(
                request("request_two")
            )), JSON_UTF_8).withStatusCode(200))
        );
        // then - correct expectations removed
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), is(expectationOne));
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_two")), is(expectationTwo));
        // then - activity logged
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationOne);
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_two"), "creating expectation:{}", expectationTwo);
        verify(mockLogFormatter).info(CLEARED, request("request_one"), "clearing recorded requests and logs that match:{}", request("request_one"));
    }

    @Test
    public void shouldClearExpectationsOnly() {
        // given - a request
        HttpRequest request = request()
            .withQueryStringParameter("type", "expectations")
            .withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some existing expectations
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond(response("response_two"));
        httpStateHandler.add(expectationTwo);
        // given - some log entries
        httpStateHandler.log(new RequestLogEntry(request("request_one")));
        httpStateHandler.log(new RequestLogEntry(request("request_two")));

        // when
        httpStateHandler.clear(request);

        // then - correct log entries removed
        assertThat(
            httpStateHandler.retrieve(request().withBody(httpRequestSerializer.serialize(request("request_one")))),
            is(response().withBody(httpRequestSerializer.serialize(Collections.singletonList(
                request("request_one")
            )), JSON_UTF_8).withStatusCode(200))
        );
        assertThat(
            httpStateHandler.retrieve(request().withBody(httpRequestSerializer.serialize(request("request_two")))),
            is(response().withBody(httpRequestSerializer.serialize(Collections.singletonList(
                request("request_two")
            )), JSON_UTF_8).withStatusCode(200))
        );
        // then - correct expectations removed
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), nullValue());
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_two")), is(expectationTwo));
        // then - activity logged
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationOne);
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_two"), "creating expectation:{}", expectationTwo);
        verify(mockLogFormatter).info(CLEARED, request("request_one"), "clearing expectations that match:{}", request("request_one"));
    }

    @Test
    public void shouldThrowExceptionForInvalidClearType() {
        // given
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("\"invalid\" is not a valid value for \"type\" parameter, only the following values are supported [log, expectations, all]"));

        // when
        httpStateHandler.clear(request().withQueryStringParameter("type", "invalid"));
    }

    @Test
    public void shouldRetrieveRecordedRequestsAsJson() {
        // given - a request
        HttpRequest request = request()
            .withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some existing expectations
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond(response("response_two"));
        httpStateHandler.add(expectationTwo);
        // given - some log entries
        httpStateHandler.log(new RequestLogEntry(request("request_one")));
        httpStateHandler.log(new RequestLogEntry(request("request_two")));
        httpStateHandler.log(new RequestLogEntry(request("request_one")));

        // when
        HttpResponse response = httpStateHandler.retrieve(request);

        // then
        assertThat(response,
            is(response().withBody(httpRequestSerializer.serialize(Arrays.asList(
                request("request_one"),
                request("request_one")
            )), JSON_UTF_8).withStatusCode(200))
        );
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationOne);
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_two"), "creating expectation:{}", expectationTwo);
        verify(mockLogFormatter).info(RETRIEVED, request("request_one"), "retrieving requests in json that match:{}", request("request_one"));
    }

    @Test
    public void shouldRetrieveRecordedRequestsAsJava() {
        // given - a request
        HttpRequest request = request()
            .withQueryStringParameter("format", "java")
            .withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some existing expectations
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond(response("response_two"));
        httpStateHandler.add(expectationTwo);
        // given - some log entries
        httpStateHandler.log(new RequestLogEntry(request("request_one")));
        httpStateHandler.log(new RequestLogEntry(request("request_two")));
        httpStateHandler.log(new RequestLogEntry(request("request_one")));

        // when
        HttpResponse response = httpStateHandler.retrieve(request);

        // then
        assertThat(response,
            is(response().withBody(httpRequestToJavaSerializer.serialize(Arrays.asList(
                request("request_one"),
                request("request_one")
            )), MediaType.create("application", "java").withCharset(UTF_8)).withStatusCode(200))
        );
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationOne);
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_two"), "creating expectation:{}", expectationTwo);
        verify(mockLogFormatter).info(RETRIEVED, request("request_one"), "retrieving requests in java that match:{}", request("request_one"));
    }

    @Test
    public void shouldRetrieveRecordedExpectationsAsJson() {
        // given - a request
        HttpRequest request = request()
            .withQueryStringParameter("type", "recorded_expectations")
            .withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some log entries
        httpStateHandler.log(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        httpStateHandler.log(new RequestResponseLogEntry(request("request_two"), response("response_two")));
        httpStateHandler.log(new RequestResponseLogEntry(request("request_one"), response("request_three")));

        // when
        HttpResponse response = httpStateHandler.retrieve(request);

        // then
        assertThat(response,
            is(response().withBody(httpExpectationSerializer.serialize(Arrays.asList(
                new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_one")),
                new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited()).thenRespond(response("request_three"))
            )), JSON_UTF_8).withStatusCode(200))
        );
        verify(mockLogFormatter).info(RETRIEVED, request("request_one"), "retrieving recorded_expectations in json that match:{}", request("request_one"));
    }

    @Test
    public void shouldRetrieveRecordedExpectationsAsJava() {
        // given - a request
        HttpRequest request = request()
            .withQueryStringParameter("type", "recorded_expectations")
            .withQueryStringParameter("format", "java")
            .withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some log entries
        httpStateHandler.log(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        httpStateHandler.log(new RequestResponseLogEntry(request("request_two"), response("response_two")));
        httpStateHandler.log(new RequestResponseLogEntry(request("request_one"), response("request_three")));

        // when
        HttpResponse response = httpStateHandler.retrieve(request);

        // then
        assertThat(response,
            is(response().withBody(httpExpectationToJavaSerializer.serialize(Arrays.asList(
                new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_one")),
                new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited()).thenRespond(response("request_three"))
            )), MediaType.create("application", "java").withCharset(UTF_8)).withStatusCode(200))
        );
        verify(mockLogFormatter).info(RETRIEVED, request("request_one"), "retrieving recorded_expectations in java that match:{}", request("request_one"));
    }

    @Test
    public void shouldRetrieveActiveExpectationsAsJson() {
        // given - a request
        HttpRequest request = request()
            .withQueryStringParameter("type", "active_expectations")
            .withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some existing expectations
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond(response("response_two"));
        httpStateHandler.add(expectationTwo);
        Expectation expectationThree = new Expectation(request("request_one")).thenRespond(response("request_three"));
        httpStateHandler.add(expectationThree);
        // given - some log entries
        httpStateHandler.log(new ExpectationMatchLogEntry(request("request_one"), expectationOne));
        httpStateHandler.log(new ExpectationMatchLogEntry(request("request_two"), expectationTwo));

        // when
        HttpResponse response = httpStateHandler.retrieve(request);

        // then
        assertThat(response,
            is(response().withBody(httpExpectationSerializer.serialize(Arrays.asList(
                expectationOne,
                expectationThree
            )), JSON_UTF_8).withStatusCode(200))
        );
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationOne);
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_two"), "creating expectation:{}", expectationTwo);
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationThree);
        verify(mockLogFormatter).info(RETRIEVED, request("request_one"), "retrieving active_expectations in json that match:{}", request("request_one"));
    }

    @Test
    public void shouldRetrieveActiveExpectationsAsJava() {
        // given - a request
        HttpRequest request = request()
            .withQueryStringParameter("type", "active_expectations")
            .withQueryStringParameter("format", "java")
            .withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some existing expectations
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond(response("response_two"));
        httpStateHandler.add(expectationTwo);
        Expectation expectationThree = new Expectation(request("request_one")).thenRespond(response("request_three"));
        httpStateHandler.add(expectationThree);
        // given - some log entries
        httpStateHandler.log(new ExpectationMatchLogEntry(request("request_one"), expectationOne));
        httpStateHandler.log(new ExpectationMatchLogEntry(request("request_two"), expectationTwo));

        // when
        HttpResponse response = httpStateHandler.retrieve(request);

        // then
        assertThat(response,
            is(response().withBody(httpExpectationToJavaSerializer.serialize(Arrays.asList(
                expectationOne,
                expectationThree
            )), MediaType.create("application", "java").withCharset(UTF_8)).withStatusCode(200))
        );
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationOne);
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_two"), "creating expectation:{}", expectationTwo);
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationThree);
        verify(mockLogFormatter).info(RETRIEVED, request("request_one"), "retrieving active_expectations in java that match:{}", request("request_one"));
    }

    @Test
    public void shouldRetrieveLogs() {
        // given - a request
        HttpRequest request = request()
            .withQueryStringParameter("type", "logs")
            .withBody(httpRequestSerializer.serialize(request("request_one")));
        // given - some log messages
        MessageLogEntry logEntryOne = new MessageLogEntry(TRACE, null, request("request_one"), "message_one");
        httpStateHandler.log(logEntryOne);
        MessageLogEntry logEntryTwo = new MessageLogEntry(TRACE, null, request("request_one"), "message_two");
        httpStateHandler.log(logEntryTwo);
        MessageLogEntry logEntryThree = new MessageLogEntry(TRACE, null, request("request_one"), "message_three");
        httpStateHandler.log(logEntryThree);

        // when
        HttpResponse response = httpStateHandler.retrieve(request);

        // then
        assertThat(response,
            is(response().withBody(
                logEntryOne.getTimeStamp() + " - " + logEntryOne.getMessage() + "\n------------------------------------\n" +
                    logEntryTwo.getTimeStamp() + " - " + logEntryTwo.getMessage() + "\n------------------------------------\n" +
                    logEntryThree.getTimeStamp() + " - " + logEntryThree.getMessage() + "\n",
                PLAIN_TEXT_UTF_8).withStatusCode(200))
        );
        verify(mockLogFormatter).info(RETRIEVED, request("request_one"), "retrieving logs that match:{}", request("request_one"));
    }

    @Test
    public void shouldThrowExceptionForInvalidRetrieveType() {
        // given
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("\"invalid\" is not a valid value for \"type\" parameter, only the following values are supported [logs, requests, recorded_expectations, active_expectations]"));

        // when
        httpStateHandler.retrieve(request().withQueryStringParameter("type", "invalid"));
    }

    @Test
    public void shouldThrowExceptionForInvalidRetrieveFormat() {
        // given
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("\"invalid\" is not a valid value for \"format\" parameter, only the following values are supported [java, json]"));

        // when
        httpStateHandler.retrieve(request().withQueryStringParameter("format", "invalid"));
    }

    @Test
    public void shouldReset() {
        // given - a request
        HttpRequest request = request();
        // given - some existing expectations
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        // given - some log entries
        httpStateHandler.log(new RequestLogEntry(request("request_one")));

        // when
        httpStateHandler.reset();

        // then - correct log entries removed
        assertThat(httpStateHandler.retrieve(request), is(response().withBody("[]", JSON_UTF_8).withStatusCode(200)));
        // then - correct expectations removed
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), nullValue());
        // then - activity logged
        verify(mockLogFormatter).info(CREATED_EXPECTATION, request("request_one"), "creating expectation:{}", expectationOne);
        verify(mockLogFormatter).info(RETRIEVED, (HttpRequest) null, "retrieving requests in json that match:{}", request());
        verify(mockLogFormatter).info(EXPECTATION_NOT_MATCHED, request("request_one"), "no active expectations when receiving request:{}", request("request_one"));
        verify(mockLogFormatter).info(CLEARED, "resetting all expectations and request logs" + NEW_LINE);
    }

}
