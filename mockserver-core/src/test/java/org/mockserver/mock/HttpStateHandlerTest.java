package org.mockserver.mock;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.TimeService;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.model.RetrieveType;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.*;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LOG_DATE_FORMAT;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.model.Format.LOG_ENTRIES;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.model.RetrieveType.REQUEST_RESPONSES;
import static org.slf4j.event.Level.INFO;

/**
 * @author jamesdbloom
 */
public class HttpStateHandlerTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();
    private final HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer(new MockServerLogger());
    private final HttpRequestToJavaSerializer httpRequestToJavaSerializer = new HttpRequestToJavaSerializer();
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());
    private final ExpectationToJavaSerializer expectationToJavaSerializer = new ExpectationToJavaSerializer();
    private final PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());
    private final VerificationSerializer verificationSerializer = new VerificationSerializer(new MockServerLogger());
    private final VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer(new MockServerLogger());

    @InjectMocks
    private HttpStateHandler httpStateHandler;

    @BeforeClass
    public static void fixTime() {
        TimeService.fixedTime = true;
    }

    @Before
    public void prepareTestFixture() {
        Scheduler scheduler = mock(Scheduler.class);
        httpStateHandler = new HttpStateHandler(new MockServerLogger(), scheduler);
        initMocks(this);
    }

    private static class FakeResponseWriter extends ResponseWriter {
        public HttpResponse response;

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            this.response = response;
        }
    }

    @Test
    public void shouldHandleRetrieveRequestsRequest() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_one"))
                .setType(RECEIVED_REQUEST)
        );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        HttpRequest expectationRetrieveRequestsRequest = request("/mockserver/retrieve")
            .withMethod("PUT")
            .withBody(
                httpRequestSerializer.serialize(request("request_one"))
            );
        boolean handle = httpStateHandler.handle(expectationRetrieveRequestsRequest, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(200));
        assertThat(responseWriter.response.getBodyAsString(), is(httpRequestSerializer.serialize(Collections.singletonList(
            request("request_one")
        ))));
    }

    @Test
    public void shouldHandleClearRequest() {
        // given
        httpStateHandler.add(new Expectation(request("request_one")).thenRespond(response("response_one")));
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_one"))
                .setType(EXPECTATION_MATCHED)
        );
        HttpRequest clearRequest = request("/mockserver/clear")
            .withMethod("PUT")
            .withBody(
                httpRequestSerializer.serialize(request("request_one"))
            );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(clearRequest, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(200));
        assertThat(responseWriter.response.getBodyAsString(), is(""));
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), is(nullValue()));
        assertThat(httpStateHandler.retrieve(request("/mockserver/retrieve")
            .withMethod("PUT")
            .withBody(
                httpRequestSerializer.serialize(request("request_one"))
            )), is(response().withBody("[]", MediaType.JSON_UTF_8).withStatusCode(200)));
    }

    @Test
    public void shouldHandleReturnStatusRequest() {
        // given
        HttpRequest statusRequest = request("/mockserver/status").withMethod("PUT");
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(statusRequest, responseWriter, false);

        // then
        assertThat(handle, is(false));
        assertThat(responseWriter.response, is(nullValue()));
    }

    @Test
    public void shouldHandleBindNewPortsRequest() {
        // given
        HttpRequest statusRequest = request("/mockserver/bind")
            .withMethod("PUT")
            .withBody(portBindingSerializer.serialize(
                portBinding(1080, 1090)
            ));
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(statusRequest, responseWriter, false);

        // then
        assertThat(handle, is(false));
        assertThat(responseWriter.response, is(nullValue()));
    }

    @Test
    public void shouldHandleStopRequest() {
        // given
        HttpRequest statusRequest = request("/mockserver/stop")
            .withMethod("PUT");
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(statusRequest, responseWriter, false);

        // then
        assertThat(handle, is(false));
        assertThat(responseWriter.response, is(nullValue()));
    }

    @Test
    public void shouldHandleRetrieveRecordedExpectationsRequest() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_one"))
                .setHttpResponse(response("response_one"))
                .setExpectation(new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited(), 0).withId("key_one").thenRespond(response("response_one")))
        );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        HttpRequest expectationRetrieveExpectationsRequest = request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
            .withBody(
                httpRequestSerializer.serialize(request("request_one"))
            );
        boolean handle = httpStateHandler.handle(expectationRetrieveExpectationsRequest, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(200));
        assertThat(responseWriter.response.getBodyAsString(), is(expectationSerializer.serialize(Collections.singletonList(
            new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited(), 0).withId("key_one").thenRespond(response("response_one"))
        ))));
    }

    @Test
    public void shouldHandleRetrieveLogMessagesRequest() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            httpStateHandler.add(new Expectation(request("request_one")).withId("key_one").thenRespond(response("response_one")));
            FakeResponseWriter responseWriter = new FakeResponseWriter();

            // when
            HttpRequest retrieveLogRequest = request("/mockserver/retrieve")
                .withMethod("PUT")
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
                .withBody(
                    httpRequestSerializer.serialize(request("request_one"))
                );
            boolean handle = httpStateHandler.handle(retrieveLogRequest, responseWriter, false);

            // then
            assertThat(handle, is(true));
            assertThat(responseWriter.response.getStatusCode(), is(200));
            assertThat(
                responseWriter.response.getBodyAsString(),
                is(endsWith(LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - creating expectation:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"id\" : \"key_one\"," + NEW_LINE +
                    "    \"priority\" : 0," + NEW_LINE +
                    "    \"httpRequest\" : {" + NEW_LINE +
                    "      \"path\" : \"request_one\"" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"times\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"timeToLive\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"httpResponse\" : {" + NEW_LINE +
                    "      \"statusCode\" : 200," + NEW_LINE +
                    "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                    "      \"body\" : \"response_one\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    "" + NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"path\" : \"request_one\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE))
            );
        } finally {
            ConfigurationProperties.logLevel(originalLevel.name());
        }
    }

    @Test
    public void shouldHandleAddExpectationRequest() {
        // given
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        HttpRequest request = request("/mockserver/expectation").withMethod("PUT").withBody(
            expectationSerializer.serialize(expectationOne)
        );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(request, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(201));
        assertThat(responseWriter.response.getBodyAsString(), CoreMatchers.containsString("[ {" + NEW_LINE +
            "  \"id\" : \""));
        assertThat(responseWriter.response.getBodyAsString(), CoreMatchers.containsString("\"," + NEW_LINE +
            "  \"priority\" : 0," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"request_one\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 200," + NEW_LINE +
            "    \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "    \"body\" : \"response_one\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "} ]"));
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), is(expectationOne));
    }

    @Test
    public void shouldHandleAddOpenAPIJsonRequest() {
        // given
        HttpRequest request = request("/mockserver/openapi").withMethod("PUT").withBody(
            FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.json")
        );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(request, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(201));
        // TODO add expectations created
        assertThat(responseWriter.response.getBodyAsString(), is("[]"));
        // TODO add assertion on expectations in state
    }

    @Test
    public void shouldHandleInvalidOpenAPIJsonRequest() {
        // given
        HttpRequest request = request("/mockserver/openapi").withMethod("PUT").withBody(
            FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.json").substring(0, 100)
        );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(request, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(400));
        assertThat(responseWriter.response.getBodyAsString(), is("Error parsing OpenAPI specification:\n\n  Unexpected end-of-input in field name\n   at [Source: (String)\"{\n    \"openapi\": \"3.0.0\",\n    \"info\": {\n      \"version\": \"1.0.0\",\n      \"title\": \"Swagger Petstore\",\n      \"li\"; line: 6, column: 8]\n"));
    }


    @Test
    public void shouldHandleAddOpenAPIYamlRequest() {
        // given
        HttpRequest request = request("/mockserver/openapi").withMethod("PUT").withBody(
            FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml")
        );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(request, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(201));
        // TODO add expectations created
        assertThat(responseWriter.response.getBodyAsString(), is("[]"));
        // TODO add assertion on expectations in state
    }

    @Test
    public void shouldHandleInvalidOpenAPIYamlRequest() {
        // given
        HttpRequest request = request("/mockserver/openapi").withMethod("PUT").withBody(
            FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml").substring(0, 100)
        );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(request, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(400));
        assertThat(responseWriter.response.getBodyAsString(), is("Errors parsing OpenAPI specification:\n\n  attribute servers is not of type `array`\n  attribute paths is missing\n"));
    }

    @Test
    public void shouldHandleRetrieveActiveExpectationsRequest() {
        // given
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        HttpRequest expectationRetrieveExpectationsRequest = request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
            .withBody(
                httpRequestSerializer.serialize(request("request_one"))
            );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(expectationRetrieveExpectationsRequest, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(200));
        assertThat(responseWriter.response.getBodyAsString(), is(expectationSerializer.serialize(Collections.singletonList(
            expectationOne
        ))));
    }

    @Test
    public void shouldHandleVerifyRequest() {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("two"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        HttpRequest expectationRetrieveExpectationsRequest = request("/mockserver/verify")
            .withMethod("PUT")
            .withBody(
                verificationSerializer.serialize(
                    new Verification()
                        .withRequest(request("two"))
                )
            );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(expectationRetrieveExpectationsRequest, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(202));
        assertThat(responseWriter.response.getBodyAsString(), is(""));
    }

    @Test
    public void shouldHandleVerifyFailureRequest() {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        HttpRequest expectationRetrieveExpectationsRequest = request("/mockserver/verify")
            .withMethod("PUT")
            .withBody(
                verificationSerializer.serialize(
                    new Verification()
                        .withRequest(request("two"))
                )
            );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(expectationRetrieveExpectationsRequest, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(406));
        assertThat(responseWriter.response.getBodyAsString(), is("Request not found at least once, expected:<{" + NEW_LINE +
            "  \"path\" : \"two\"" + NEW_LINE +
            "}> but was:<{" + NEW_LINE +
            "  \"path\" : \"one\"" + NEW_LINE +
            "}>"));
    }

    @Test
    public void shouldHandleVerifySequenceRequest() {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("two"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        HttpRequest expectationRetrieveExpectationsRequest = request("/mockserver/verifySequence")
            .withMethod("PUT")
            .withBody(
                verificationSequenceSerializer.serialize(
                    new VerificationSequence()
                        .withRequests(
                            request("one"),
                            request("three")
                        )
                )
            );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(expectationRetrieveExpectationsRequest, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(202));
        assertThat(responseWriter.response.getBodyAsString(), is(""));
    }

    @Test
    public void shouldHandleVerifySequenceFailureRequest() {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("two"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        HttpRequest expectationRetrieveExpectationsRequest = request("/mockserver/verifySequence")
            .withMethod("PUT")
            .withBody(
                verificationSequenceSerializer.serialize(
                    new VerificationSequence()
                        .withRequests(
                            request("three"),
                            request("one")
                        )
                )
            );
        FakeResponseWriter responseWriter = new FakeResponseWriter();

        // when
        boolean handle = httpStateHandler.handle(expectationRetrieveExpectationsRequest, responseWriter, false);

        // then
        assertThat(handle, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(406));
        assertThat(responseWriter.response.getBodyAsString(), is("Request sequence not found, expected:<[ {" + NEW_LINE +
            "  \"path\" : \"three\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"path\" : \"one\"" + NEW_LINE +
            "} ]> but was:<[ {" + NEW_LINE +
            "  \"path\" : \"one\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"path\" : \"two\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"path\" : \"three\"" + NEW_LINE +
            "} ]>"));
    }

    @Test
    public void shouldRetrieveLogEntries() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            httpStateHandler.log(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                    .setHttpRequest(request("request_one"))
                    .setExpectation(new Expectation(request("request_one")).withId("key_one").thenRespond(response("response_two")))
                    .setMessageFormat("no expectation for:{}returning response:{}")
                    .setArguments(request("request_one"), notFoundResponse())
            );
            httpStateHandler.log(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(EXPECTATION_RESPONSE)
                    .setHttpRequest(request("request_two"))
                    .setHttpResponse(response("response_two"))
                    .setMessageFormat("returning error:{}for request:{}for action:{}")
                    .setArguments(request("request_two"), response("response_two"), response("response_two"))
            );
            httpStateHandler.log(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(EXPECTATION_MATCHED)
                    .setHttpRequest(request("request_one"))
                    .setExpectation(new Expectation(request("request_one")).withId("key_one").thenRespond(response("response_two")))
                    .setMessageFormat("request:{}matched expectation:{}")
                    .setArguments(request("request_one"), new Expectation(request("request_one")).withId("key_one").thenRespond(response("response_two")))
            );
            httpStateHandler.log(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(EXPECTATION_MATCHED)
                    .setHttpRequest(request("request_two"))
                    .setExpectation(new Expectation(request("request_two")).withId("key_two").thenRespond(response("response_two")))
                    .setMessageFormat("request:{}matched expectation:{}")
                    .setArguments(request("request_two"), new Expectation(request("request_two")).withId("key_two").thenRespond(response("response_two")))
            );
            httpStateHandler.log(
                new LogEntry()
                    .setType(TRACE)
                    .setLogLevel(INFO)
                    .setHttpRequest(request("request_four"))
                    .setExpectation(new Expectation(request("request_four")).withId("key_four").thenRespond(response("response_four")))
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );

            // when
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );

            // then
            assertThat(response,
                is(response().withBody("" +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - no expectation for:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"path\" : \"request_one\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " returning response:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"statusCode\" : 404," + NEW_LINE +
                        "    \"reasonPhrase\" : \"Not Found\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - returning error:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"path\" : \"request_two\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " for request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"statusCode\" : 200," + NEW_LINE +
                        "    \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "    \"body\" : \"response_two\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " for action:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"statusCode\" : 200," + NEW_LINE +
                        "    \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "    \"body\" : \"response_two\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"path\" : \"request_one\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " matched expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"id\" : \"key_one\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"request_one\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"statusCode\" : 200," + NEW_LINE +
                        "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "      \"body\" : \"response_two\"" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"path\" : \"request_two\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " matched expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"id\" : \"key_two\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"request_two\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"statusCode\" : 200," + NEW_LINE +
                        "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "      \"body\" : \"response_two\"" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - some random" + NEW_LINE +
                        NEW_LINE +
                        "  argument_one" + NEW_LINE +
                        NEW_LINE +
                        " message" + NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                        "" + NEW_LINE +
                        "  { }" + NEW_LINE +
                        NEW_LINE,
                    MediaType.PLAIN_TEXT_UTF_8).withStatusCode(200))
            );
        } finally {
            ConfigurationProperties.logLevel(originalLevel.name());
        }
    }

    @Test
    public void shouldRetrieveLogEntriesWithRequestMatcher() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            httpStateHandler.log(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                    .setHttpRequest(request("request_one"))
                    .setExpectation(new Expectation(request("request_one")).withId("key_one").thenRespond(response("response_two")))
                    .setMessageFormat("no expectation for:{}returning response:{}")
                    .setArguments(request("request_one"), notFoundResponse())
            );
            httpStateHandler.log(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(EXPECTATION_RESPONSE)
                    .setHttpRequest(request("request_two"))
                    .setHttpResponse(response("response_two"))
                    .setMessageFormat("returning error:{}for request:{}for action:{}")
                    .setArguments(request("request_two"), response("response_two"), response("response_two"))
            );
            httpStateHandler.log(
                new LogEntry()
                    .setType(EXPECTATION_MATCHED)
                    .setLogLevel(INFO)
                    .setHttpRequest(request("request_one"))
                    .setExpectation(new Expectation(request("request_one")).withId("key_one").thenRespond(response("response_two")))
                    .setMessageFormat("request:{}matched expectation:{}")
                    .setArguments(request("request_one"), new Expectation(request("request_one")).withId("key_one").thenRespond(response("response_two")))
            );
            httpStateHandler.log(
                new LogEntry()
                    .setType(EXPECTATION_MATCHED)
                    .setLogLevel(INFO)
                    .setHttpRequest(request("request_two"))
                    .setExpectation(new Expectation(request("request_two")).withId("key_two").thenRespond(response("response_two")))
                    .setMessageFormat("request:{}matched expectation:{}")
                    .setArguments(request("request_two"), new Expectation(request("request_two")).withId("key_two").thenRespond(response("response_two")))
            );
            httpStateHandler.log(
                new LogEntry()
                    .setType(TRACE)
                    .setHttpRequest(request("request_four"))
                    .setExpectation(new Expectation(request("request_four")).withId("key_four").thenRespond(response("response_four")))
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );

            // when
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                        .withBody(httpRequestSerializer.serialize(request("request_one")))
                );

            // then
            assertThat(response,
                is(response().withBody("" +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - no expectation for:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"path\" : \"request_one\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " returning response:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"statusCode\" : 404," + NEW_LINE +
                        "    \"reasonPhrase\" : \"Not Found\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"path\" : \"request_one\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " matched expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"id\" : \"key_one\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"request_one\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"statusCode\" : 200," + NEW_LINE +
                        "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "      \"body\" : \"response_two\"" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                        "" + NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"path\" : \"request_one\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE,
                    MediaType.PLAIN_TEXT_UTF_8).withStatusCode(200))
            );
        } finally {
            ConfigurationProperties.logLevel(originalLevel.name());
        }
    }

    @Test
    public void shouldThrowExceptionForInvalidRetrieveType() {
        try {
            // when
            httpStateHandler.retrieve(request().withQueryStringParameter("type", "invalid"));
            fail("expected exception to be thrown");
        } catch (Throwable throwable) {
            // then
            assertThat(throwable, instanceOf(IllegalArgumentException.class));
            assertThat(throwable.getMessage(), is("\"invalid\" is not a valid value for \"type\" parameter, only the following values are supported [logs, requests, request_responses, recorded_expectations, active_expectations]"));
        }
    }

    @Test
    public void shouldThrowExceptionForInvalidRetrieveFormat() {
        try {
            // when
            httpStateHandler.retrieve(request().withQueryStringParameter("format", "invalid"));
            fail("expected exception to be thrown");
        } catch (Throwable throwable) {
            // then
            assertThat(throwable, instanceOf(IllegalArgumentException.class));
            assertThat(throwable.getMessage(), is("\"invalid\" is not a valid value for \"format\" parameter, only the following values are supported [java, json, log_entries]"));
        }
    }

    @Test
    public void shouldAllowAddingOfExceptionsWithNullFields() {
        // given - some existing expectations
        Expectation expectationOne = new Expectation(null).thenRespond(response("response_one"));
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond((HttpResponse) null);

        // when
        httpStateHandler.add(expectationOne);
        httpStateHandler.add(expectationTwo);

        // then - correct expectations exist
        assertThat(httpStateHandler.firstMatchingExpectation(null), is(expectationOne));
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_two")), is(expectationOne));
    }

    @Test
    public void shouldClearLogsAndExpectations() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            httpStateHandler.add(
                new Expectation(request("request_one"))
                    .withId("one")
                    .thenRespond(response("response_one"))
            );
            // given - some log entries
            httpStateHandler.log(
                new LogEntry()
                    .setType(TRACE)
                    .setHttpRequest(request("request_four"))
                    .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );

            // when
            httpStateHandler
                .clear(
                    request()
                        .withQueryStringParameter("type", "all")
                );

            // then - retrieves correct state
            assertThat(
                httpStateHandler
                    .retrieve(
                        request()
                            .withQueryStringParameter("type", "logs")
                    ),
                is(response().withBody("" +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - removed expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"id\" : \"one\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"request_one\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"statusCode\" : 200," + NEW_LINE +
                        "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "      \"body\" : \"response_one\"" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - clearing expectations and logs that match:" + NEW_LINE +
                        NEW_LINE +
                        "  {}" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                        NEW_LINE +
                        "  { }" + NEW_LINE +
                        NEW_LINE,
                    MediaType.PLAIN_TEXT_UTF_8).withStatusCode(200))
            );
            assertThat(
                httpStateHandler
                    .retrieve(
                        request()
                            .withQueryStringParameter("type", "active_expectations")
                    ),
                is(response().withBody("[]", MediaType.JSON_UTF_8).withStatusCode(200))
            );
            assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), nullValue());
        } finally {
            ConfigurationProperties.logLevel(originalLevel.name());
        }
    }

    @Test
    public void shouldClearLogsAndExpectationsWithRequestMatcher() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            httpStateHandler.add(
                new Expectation(request("request_one"))
                    .withId("key_one")
                    .thenRespond(response("response_one"))
            );
            httpStateHandler.add(
                new Expectation(request("request_four"))
                    .withId("key_four")
                    .thenRespond(response("response_four"))
            );
            // given - some log entries
            httpStateHandler.log(
                new LogEntry()
                    .setType(TRACE)
                    .setLogLevel(INFO)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );
            httpStateHandler.log(
                new LogEntry()
                    .setType(TRACE)
                    .setLogLevel(INFO)
                    .setHttpRequest(request("request_four"))
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_four")
            );

            // when
            httpStateHandler
                .clear(
                    request()
                        .withQueryStringParameter("type", "all")
                        .withBody(httpRequestSerializer.serialize(request("request_four")))
                );

            // then - retrieves correct state
            assertThat(
                httpStateHandler
                    .retrieve(
                        request()
                            .withQueryStringParameter("type", "logs")
                    ),
                is(response().withBody("" +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - creating expectation:" + NEW_LINE +
                        "" + NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"id\" : \"key_one\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"request_one\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"statusCode\" : 200," + NEW_LINE +
                        "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "      \"body\" : \"response_one\"" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }" + NEW_LINE +
                        "" + NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - some random" + NEW_LINE +
                        "" + NEW_LINE +
                        "  argument_one" + NEW_LINE +
                        "" + NEW_LINE +
                        " message" + NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - removed expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"id\" : \"key_four\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"request_four\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"statusCode\" : 200," + NEW_LINE +
                        "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "      \"body\" : \"response_four\"" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - clearing expectations and logs that match:" + NEW_LINE +
                        "" + NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"path\" : \"request_four\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        "" + NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                        "" + NEW_LINE +
                        "  { }" + NEW_LINE +
                        NEW_LINE,
                    MediaType.PLAIN_TEXT_UTF_8).withStatusCode(200))
            );
            assertThat(
                httpStateHandler
                    .retrieve(
                        request()
                            .withQueryStringParameter("type", "active_expectations")
                    ),
                is(response().withBody("" +
                        "[ {" + NEW_LINE +
                        "  \"id\" : \"key_one\"," + NEW_LINE +
                        "  \"priority\" : 0," + NEW_LINE +
                        "  \"httpRequest\" : {" + NEW_LINE +
                        "    \"path\" : \"request_one\"" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"httpResponse\" : {" + NEW_LINE +
                        "    \"statusCode\" : 200," + NEW_LINE +
                        "    \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "    \"body\" : \"response_one\"" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"times\" : {" + NEW_LINE +
                        "    \"unlimited\" : true" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"timeToLive\" : {" + NEW_LINE +
                        "    \"unlimited\" : true" + NEW_LINE +
                        "  }" + NEW_LINE +
                        "} ]",
                    MediaType.JSON_UTF_8).withStatusCode(200))
            );
            assertThat(
                httpStateHandler.firstMatchingExpectation(request("request_one")),
                is(
                    new Expectation(request("request_one"))
                        .thenRespond(response("response_one"))
                )
            );
            assertThat(
                httpStateHandler.firstMatchingExpectation(request("request_four")),
                nullValue()
            );
        } finally {
            ConfigurationProperties.logLevel(originalLevel.name());
        }
    }

    @Test
    public void shouldClearLogsOnly() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            httpStateHandler.add(
                new Expectation(request("request_one"))
                    .withId("key_one")
                    .thenRespond(response("response_one"))
            );
            // given - some log entries
            httpStateHandler.log(
                new LogEntry()
                    .setType(TRACE)
                    .setHttpRequest(request("request_four"))
                    .setExpectation(new Expectation(request("request_four")).withId("key_four").thenRespond(response("response_four")))
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );

            // when
            httpStateHandler
                .clear(
                    request()
                        .withQueryStringParameter("type", "log")
                );

            // then - retrieves correct state
            assertThat(
                httpStateHandler
                    .retrieve(
                        request()
                            .withQueryStringParameter("type", "logs")
                    ),
                is(response().withBody("" +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - clearing logs that match:" + NEW_LINE +
                        NEW_LINE +
                        "  {}" + NEW_LINE +
                        NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                        "" + NEW_LINE +
                        "  { }" + NEW_LINE +
                        NEW_LINE,
                    MediaType.PLAIN_TEXT_UTF_8).withStatusCode(200))
            );
            assertThat(
                httpStateHandler
                    .retrieve(
                        request()
                            .withQueryStringParameter("type", "active_expectations")
                    ),
                is(response().withBody("" +
                        "[ {" + NEW_LINE +
                        "  \"id\" : \"key_one\"," + NEW_LINE +
                        "  \"priority\" : 0," + NEW_LINE +
                        "  \"httpRequest\" : {" + NEW_LINE +
                        "    \"path\" : \"request_one\"" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"httpResponse\" : {" + NEW_LINE +
                        "    \"statusCode\" : 200," + NEW_LINE +
                        "    \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                        "    \"body\" : \"response_one\"" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"times\" : {" + NEW_LINE +
                        "    \"unlimited\" : true" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"timeToLive\" : {" + NEW_LINE +
                        "    \"unlimited\" : true" + NEW_LINE +
                        "  }" + NEW_LINE +
                        "} ]",
                    MediaType.JSON_UTF_8).withStatusCode(200))
            );
            assertThat(
                httpStateHandler.firstMatchingExpectation(request("request_one")),
                is(
                    new Expectation(request("request_one"))
                        .thenRespond(response("response_one"))
                )
            );
        } finally {
            ConfigurationProperties.logLevel(originalLevel.name());
        }
    }

    @Test
    public void shouldClearExpectationsOnly() {
        // given
        httpStateHandler.add(new Expectation(request("request_one")).thenRespond(response("response_one")));
        httpStateHandler.add(new Expectation(request("request_two")).thenRespond(response("response_two")));
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_one"))
                .setHttpResponse(response("response_one"))
                .setType(EXPECTATION_RESPONSE)
        );
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_two"))
                .setHttpError(error().withResponseBytes("response_two".getBytes(UTF_8)))
                .setType(EXPECTATION_RESPONSE)
        );

        // when
        httpStateHandler
            .clear(
                request()
                    .withQueryStringParameter("type", "expectations")
                    .withBody(httpRequestSerializer.serialize(request("request_one")))
            );

        // then - correct log entries removed
        HttpResponse retrieve = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", REQUEST_RESPONSES.name())
                    .withQueryStringParameter("format", LOG_ENTRIES.name())
                    .withBody(httpRequestSerializer.serialize(request("request_one")))
            );
        assertThat(
            retrieve.getBodyAsString(),
            is(new LogEntrySerializer(new MockServerLogger()).serialize(Collections.singletonList(
                new LogEntry()
                    .setHttpRequest(request("request_one"))
                    .setHttpResponse(response("response_one"))
                    .setType(EXPECTATION_RESPONSE)
            )))
        );
        // then - correct expectations removed
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), nullValue());
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_two")), is(new Expectation(request("request_two")).thenRespond(response("response_two"))));
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
        // given
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_one"))
                .setType(RECEIVED_REQUEST)
        );
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_two"))
                .setType(RECEIVED_REQUEST)
        );
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_one"))
                .setType(RECEIVED_REQUEST)
        );

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withBody(httpRequestSerializer.serialize(request("request_one")))
            );

        // then
        assertThat(response,
            is(response().withBody(httpRequestSerializer.serialize(Arrays.asList(
                request("request_one"),
                request("request_one")
            )), MediaType.JSON_UTF_8).withStatusCode(200))
        );
    }

    @Test
    public void shouldRetrieveRecordedRequestsAsLogEntries() {
        // given
        httpStateHandler
            .log(
                new LogEntry()
                    .setType(RECEIVED_REQUEST)
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat("received request:{}")
                    .setArguments(request("request_one"))
            );
        httpStateHandler
            .log(
                new LogEntry()
                    .setType(RECEIVED_REQUEST)
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(request("request_two"))
                    .setMessageFormat("received request:{}")
                    .setArguments(request("request_two"))
            );
        httpStateHandler
            .log(
                new LogEntry()
                    .setType(RECEIVED_REQUEST)
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat("received request:{}")
                    .setArguments(request("request_one"))
            );

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("format", "log_entries")
                    .withBody(httpRequestSerializer.serialize(request("request_one")))
            );

        // then
        assertThat(response,
            is(response().withBody(new LogEntrySerializer(new MockServerLogger()).serialize(Arrays.asList(
                new LogEntry()
                    .setType(RECEIVED_REQUEST)
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat("received request:{}")
                    .setArguments(request("request_one")),
                new LogEntry()
                    .setType(RECEIVED_REQUEST)
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat("received request:{}")
                    .setArguments(request("request_one"))
            )), MediaType.JSON_UTF_8).withStatusCode(200))
        );
    }

    @Test
    public void shouldRetrieveRecordedRequestsAsJava() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_one"))
                .setType(RECEIVED_REQUEST)
        );
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_two"))
                .setType(RECEIVED_REQUEST)
        );
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_one"))
                .setType(RECEIVED_REQUEST)
        );

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("format", "java")
                    .withBody(httpRequestSerializer.serialize(request("request_one")))
            );

        // then
        assertThat(response,
            is(response().withBody(httpRequestToJavaSerializer.serialize(Arrays.asList(
                request("request_one"),
                request("request_one")
            )), MediaType.create("application", "java").withCharset(UTF_8)).withStatusCode(200))
        );
    }

    @Test
    public void shouldRetrieveRecordedRequestResponsesAsJson() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        );
        httpStateHandler.log(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("returning error:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two"))
        );

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "request_responses")
                    .withBody(httpRequestSerializer.serialize(request("request_.*")))
            );

        // then
        assertThat(response,
            is(response().withBody("[" + NEW_LINE +
                "  {" + NEW_LINE +
                "    \"timestamp\" : \"" + LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + "\"," + NEW_LINE +
                "    \"httpRequest\" : {" + NEW_LINE +
                "      \"path\" : \"request_one\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  {" + NEW_LINE +
                "    \"timestamp\" : \"" + LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + "\"," + NEW_LINE +
                "    \"httpRequest\" : {" + NEW_LINE +
                "      \"path\" : \"request_two\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\" : {" + NEW_LINE +
                "      \"statusCode\" : 200," + NEW_LINE +
                "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                "      \"body\" : \"response_two\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }" + NEW_LINE +
                "]", MediaType.JSON_UTF_8).withStatusCode(200))
        );
    }

    @Test
    public void shouldRetrieveRecordedRequestsResponsesAsLogEntries() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).withId("key_one").thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        );
        httpStateHandler.log(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("returning error:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two"))
        );

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "request_responses")
                    .withQueryStringParameter("format", "log_entries")
                    .withBody(httpRequestSerializer.serialize(request("request_.*")))
            );

        // then
        assertThat(response,
            is(response().withBody("[" + NEW_LINE +
                "  {" + NEW_LINE +
                "    \"logLevel\" : \"INFO\"," + NEW_LINE +
                "    \"timestamp\" : \"" + LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + "\"," + NEW_LINE +
                "    \"type\" : \"EXPECTATION_NOT_MATCHED_RESPONSE\"," + NEW_LINE +
                "    \"httpRequest\" : {" + NEW_LINE +
                "      \"path\" : \"request_one\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"expectation\" : {" + NEW_LINE +
                "      \"id\" : \"key_one\"," + NEW_LINE +
                "      \"priority\" : 0," + NEW_LINE +
                "      \"httpRequest\" : {" + NEW_LINE +
                "        \"path\" : \"request_one\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"times\" : {" + NEW_LINE +
                "        \"unlimited\" : true" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"timeToLive\" : {" + NEW_LINE +
                "        \"unlimited\" : true" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\" : {" + NEW_LINE +
                "        \"statusCode\" : 200," + NEW_LINE +
                "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                "        \"body\" : \"response_two\"" + NEW_LINE +
                "      }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"message\" : [" + NEW_LINE +
                "      \"no expectation for:\"," + NEW_LINE +
                "      \"\"," + NEW_LINE +
                "      \"   {\"," + NEW_LINE +
                "      \"      \\\"path\\\" : \\\"request_one\\\"\"," + NEW_LINE +
                "      \"   }\"," + NEW_LINE +
                "      \"\"," + NEW_LINE +
                "      \" returning response:\"," + NEW_LINE +
                "      \"\"," + NEW_LINE +
                "      \"   {\"," + NEW_LINE +
                "      \"      \\\"statusCode\\\" : 404,\"," + NEW_LINE +
                "      \"      \\\"reasonPhrase\\\" : \\\"Not Found\\\"\"," + NEW_LINE +
                "      \"   }\"" + NEW_LINE +
                "    ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  {" + NEW_LINE +
                "    \"logLevel\" : \"INFO\"," + NEW_LINE +
                "    \"timestamp\" : \"" + LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + "\"," + NEW_LINE +
                "    \"type\" : \"EXPECTATION_RESPONSE\"," + NEW_LINE +
                "    \"httpRequest\" : {" + NEW_LINE +
                "      \"path\" : \"request_two\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\" : {" + NEW_LINE +
                "      \"statusCode\" : 200," + NEW_LINE +
                "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                "      \"body\" : \"response_two\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"message\" : [" + NEW_LINE +
                "      \"returning error:\"," + NEW_LINE +
                "      \"\"," + NEW_LINE +
                "      \"   {\"," + NEW_LINE +
                "      \"      \\\"path\\\" : \\\"request_two\\\"\"," + NEW_LINE +
                "      \"   }\"," + NEW_LINE +
                "      \"\"," + NEW_LINE +
                "      \" for request:\"," + NEW_LINE +
                "      \"\"," + NEW_LINE +
                "      \"   {\"," + NEW_LINE +
                "      \"      \\\"statusCode\\\" : 200,\"," + NEW_LINE +
                "      \"      \\\"reasonPhrase\\\" : \\\"OK\\\",\"," + NEW_LINE +
                "      \"      \\\"body\\\" : \\\"response_two\\\"\"," + NEW_LINE +
                "      \"   }\"," + NEW_LINE +
                "      \"\"," + NEW_LINE +
                "      \" for action:\"," + NEW_LINE +
                "      \"\"," + NEW_LINE +
                "      \"   {\"," + NEW_LINE +
                "      \"      \\\"statusCode\\\" : 200,\"," + NEW_LINE +
                "      \"      \\\"reasonPhrase\\\" : \\\"OK\\\",\"," + NEW_LINE +
                "      \"      \\\"body\\\" : \\\"response_two\\\"\"," + NEW_LINE +
                "      \"   }\"" + NEW_LINE +
                "    ]" + NEW_LINE +
                "  }" + NEW_LINE +
                "]", MediaType.JSON_UTF_8).withStatusCode(200))
        );
    }

    @Test
    public void shouldRetrieveRecordedRequestsResponsesAsJava() {
        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("format", "java")
                    .withQueryStringParameter("type", "request_responses")
                    .withBody(httpRequestSerializer.serialize(request("request_one")))
            );

        // then
        assertThat(response.getBodyAsString(), is("JAVA not supported for REQUEST_RESPONSES"));
    }

    @Test
    public void shouldRetrieveRecordedExpectationsAsJson() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request("request_one"))
                .setHttpResponse(response("response_one"))
                .setExpectation(new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited(), 0).withId("key_one").thenRespond(response("response_one")))
        );
        httpStateHandler.log(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setExpectation(new Expectation(request("request_two"), Times.once(), TimeToLive.unlimited(), 0).withId("key_two").thenRespond(response("response_two")))
        );

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "recorded_expectations")
                    .withQueryStringParameter("format", "json")
            );

        // then
        assertThat(response,
            is(response().withBody(expectationSerializer.serialize(Arrays.asList(
                new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited(), 0).withId("key_one").thenRespond(response("response_one")),
                new Expectation(request("request_two"), Times.once(), TimeToLive.unlimited(), 0).withId("key_two").thenRespond(response("response_two"))
            )), MediaType.JSON_UTF_8).withStatusCode(200))
        );
    }

    @Test
    public void shouldRetrieveRecordedExpectationsAsJava() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request("request_one"))
                .setHttpResponse(response("response_one"))
                .setExpectation(request("request_one"), response("response_one"))
        );
        httpStateHandler.log(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setExpectation(request("request_two"), response("response_two"))
        );

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "recorded_expectations")
                    .withQueryStringParameter("format", "java")
            );

        // then
        assertThat(response,
            is(response().withBody(expectationToJavaSerializer.serialize(Arrays.asList(
                new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("response_one")),
                new Expectation(request("request_two"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("response_two"))
            )), MediaType.create("application", "java").withCharset(UTF_8)).withStatusCode(200))
        );
    }

    @Test
    public void shouldRetrieveRecordedExpectationsAsJavaWithRequestMatcher() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request("request_one"))
                .setHttpResponse(response("response_one"))
                .setExpectation(request("request_one"), response("response_one"))
        );
        httpStateHandler.log(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setExpectation(request("request_two"), response("response_two"))
        );

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "recorded_expectations")
                    .withQueryStringParameter("format", "java")
                    .withBody(httpRequestSerializer.serialize(request("request_one")))
            );

        // then
        assertThat(response,
            is(response().withBody(expectationToJavaSerializer.serialize(Collections.singletonList(
                new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("response_one"))
            )), MediaType.create("application", "java").withCharset(UTF_8)).withStatusCode(200))
        );
    }

    @Test
    public void shouldRetrieveActiveExpectationsAsJson() {
        // given
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond(response("response_two"));
        httpStateHandler.add(expectationTwo);
        Expectation expectationThree = new Expectation(request("request_one")).thenRespond(response("request_three"));
        httpStateHandler.add(expectationThree);

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "active_expectations")
                    .withBody(httpRequestSerializer.serialize(request("request_one")))
            );

        // then
        assertThat(response,
            is(response().withBody(expectationSerializer.serialize(Arrays.asList(
                expectationOne,
                expectationThree
            )), MediaType.JSON_UTF_8).withStatusCode(200))
        );
    }

    @Test
    public void shouldRetrieveActiveExpectationsAsJava() {
        // given
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        Expectation expectationTwo = new Expectation(request("request_two")).thenRespond(response("response_two"));
        httpStateHandler.add(expectationTwo);
        Expectation expectationThree = new Expectation(request("request_one")).thenRespond(response("request_three"));
        httpStateHandler.add(expectationThree);

        // when
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "active_expectations")
                    .withQueryStringParameter("format", "java")
                    .withBody(httpRequestSerializer.serialize(request("request_one")))
            );

        // then
        assertThat(response,
            is(response().withBody(expectationToJavaSerializer.serialize(Arrays.asList(
                expectationOne,
                expectationThree
            )), MediaType.create("application", "java").withCharset(UTF_8)).withStatusCode(200))
        );
    }

    @Test
    public void shouldReset() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            httpStateHandler.add(
                new Expectation(request("request_one"))
                    .thenRespond(response("response_one"))
            );
            // given - some log entries
            httpStateHandler.log(
                new LogEntry()
                    .setType(TRACE)
                    .setHttpRequest(request("request_four"))
                    .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );

            // when
            httpStateHandler.reset();

            // then - retrieves correct state
            assertThat(
                httpStateHandler
                    .retrieve(
                        request()
                            .withQueryStringParameter("type", "logs")
                    ),
                is(response().withBody("" +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - resetting all expectations and request logs" + NEW_LINE +
                        "------------------------------------" + NEW_LINE +
                        LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                        "" + NEW_LINE +
                        "  { }" + NEW_LINE +
                        NEW_LINE,
                    MediaType.PLAIN_TEXT_UTF_8).withStatusCode(200))
            );
            assertThat(
                httpStateHandler
                    .retrieve(
                        request()
                            .withQueryStringParameter("type", "active_expectations")
                    ),
                is(response().withBody("[]", MediaType.JSON_UTF_8).withStatusCode(200))
            );
            assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), nullValue());
        } finally {
            ConfigurationProperties.logLevel(originalLevel.name());
        }
    }

    @Test
    public void shouldVerifyWithFuture() throws Exception {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("two"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );

        // when
        String result = httpStateHandler.verify(
            new Verification()
                .withRequest(request("two"))
        ).get(5, SECONDS);

        // then
        assertThat(result, is(""));
    }

    @Test
    public void shouldVerifyWithCallback() throws Exception {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("two"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        CompletableFuture<String> verificationResult = new CompletableFuture<>();

        // when
        httpStateHandler.verify(
            new Verification()
                .withRequest(request("two")),
            verificationResult::complete
        );

        // then
        assertThat(verificationResult.get(5, SECONDS), is(""));
    }

    @Test
    public void shouldVerifyFailureWithCallback() throws Exception {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        CompletableFuture<String> verificationResult = new CompletableFuture<>();

        // when
        httpStateHandler.verify(
            new Verification()
                .withRequest(request("two")),
            verificationResult::complete
        );

        // then
        assertThat(verificationResult.get(5, SECONDS), is("Request not found at least once, expected:<{" + NEW_LINE +
            "  \"path\" : \"two\"" + NEW_LINE +
            "}> but was:<{" + NEW_LINE +
            "  \"path\" : \"one\"" + NEW_LINE +
            "}>"));
    }

    @Test
    public void shouldVerifySequenceWithFuture() throws Exception {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("two"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );

        // when
        String result = httpStateHandler.verify(
            new VerificationSequence()
                .withRequests(
                    request("one"),
                    request("three")
                )
        ).get(5, SECONDS);

        // then
        assertThat(result, is(""));
    }

    @Test
    public void shouldVerifySequenceWithCallback() throws Exception {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("two"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        CompletableFuture<String> verificationResult = new CompletableFuture<>();

        // when
        httpStateHandler.verify(
            new VerificationSequence()
                .withRequests(
                    request("one"),
                    request("three")
                ),
            verificationResult::complete
        );

        // then
        assertThat(verificationResult.get(5, SECONDS), is(""));
    }

    @Test
    public void shouldVerifySequenceFailureWithCallback() throws Exception {
        // given
        MockServerEventLog mockServerEventLog = httpStateHandler.getMockServerLog();
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("two"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        CompletableFuture<String> verificationResult = new CompletableFuture<>();

        // when
        httpStateHandler.verify(
            new VerificationSequence()
                .withRequests(
                    request("three"),
                    request("one")
                ),
            verificationResult::complete
        );

        // then
        assertThat(verificationResult.get(5, SECONDS), is("Request sequence not found, expected:<[ {" + NEW_LINE +
            "  \"path\" : \"three\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"path\" : \"one\"" + NEW_LINE +
            "} ]> but was:<[ {" + NEW_LINE +
            "  \"path\" : \"one\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"path\" : \"two\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"path\" : \"three\"" + NEW_LINE +
            "} ]>"));
    }

}
