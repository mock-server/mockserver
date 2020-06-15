package org.mockserver.mockservlet;

import com.google.common.collect.ImmutableSet;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.model.MediaType;
import org.mockserver.model.RetrieveType;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.PortBindingSerializer;
import org.mockserver.servlet.responsewriter.ServletResponseWriter;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON_UTF_8;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class MockServerServletTest {

    private final HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer(new MockServerLogger());
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());
    private final PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());

    private HttpState httpStateHandler;
    private HttpActionHandler mockActionHandler;

    @InjectMocks
    private MockServerServlet mockServerServlet;

    private MockHttpServletResponse response;

    @Before
    public void setupFixture() {
        mockActionHandler = mock(HttpActionHandler.class);
        Scheduler scheduler = mock(Scheduler.class);
        httpStateHandler = spy(new HttpState(new MockServerLogger(), scheduler));
        response = new MockHttpServletResponse();
        mockServerServlet = new MockServerServlet();


        initMocks(this);
    }

    private MockHttpServletRequest buildHttpServletRequest(String method, String requestURI, String body) {
        MockHttpServletRequest expectationRetrieveRequestsRequest = new MockHttpServletRequest(method, requestURI);
        expectationRetrieveRequestsRequest.addHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString());
        expectationRetrieveRequestsRequest.setContent(body.getBytes());
        return expectationRetrieveRequestsRequest;
    }

    private void assertResponse(MockHttpServletResponse response, int responseStatusCode, String responseBody) {
        assertThat(response.getStatus(), is(responseStatusCode));
        assertThat(new String(response.getContentAsByteArray(), UTF_8), is(responseBody));
    }

    @Test
    public void shouldRetrieveRequests() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request("request_one"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_one"))
        );
        httpStateHandler.log(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request("request_two"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_two"))
        );

        // when
        mockServerServlet
            .service(
                buildHttpServletRequest(
                    "PUT",
                    "/mockserver/retrieve",
                    httpRequestSerializer.serialize(request("request_one"))
                ),
                response
            );

        // then
        assertResponse(response, 200, httpRequestSerializer.serialize(Collections.singletonList(
            request("request_one")
        )));
    }

    @Test
    public void shouldClear() {
        // given
        httpStateHandler.add(new Expectation(request("request_one")).thenRespond(response("response_one")));
        httpStateHandler.log(
            new LogEntry()
                .setHttpRequest(request("request_one"))
                .setType(EXPECTATION_MATCHED)
        );
        MockHttpServletRequest clearRequest = buildHttpServletRequest(
            "PUT",
            "/mockserver/clear",
            httpRequestSerializer.serialize(request("request_one"))
        );

        // when
        mockServerServlet.service(clearRequest, response);

        // then
        assertResponse(response, 200, "");
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), is(nullValue()));
        assertThat(httpStateHandler.retrieve(request("/retrieve")
            .withMethod("PUT")
            .withBody(
                httpRequestSerializer.serialize(request("request_one"))
            )), is(response().withBody("[]", MediaType.JSON_UTF_8).withStatusCode(200)));
    }

    @Test
    public void shouldReturnStatus() {
        // given
        MockHttpServletRequest statusRequest = buildHttpServletRequest(
            "PUT",
            "/mockserver/status",
            ""
        );

        // when
        mockServerServlet.service(statusRequest, response);

        // then
        assertResponse(response, 200, portBindingSerializer.serialize(
            portBinding(80)
        ));
    }

    @Test
    public void shouldReturnStatusOnCustomPath() {
        String originalStatusPath = ConfigurationProperties.livenessHttpGetPath();
        try {
            // given
            ConfigurationProperties.livenessHttpGetPath("/livenessProbe");
            MockHttpServletRequest statusRequest = buildHttpServletRequest(
                "GET",
                "/livenessProbe",
                ""
            );

            // when
            mockServerServlet.service(statusRequest, response);

            // then
            assertResponse(response, 200, portBindingSerializer.serialize(
                portBinding(80)
            ));
        } finally {
            ConfigurationProperties.livenessHttpGetPath(originalStatusPath);
        }
    }

    @Test
    public void shouldBindNewPorts() {
        // given
        MockHttpServletRequest statusRequest = buildHttpServletRequest(
            "PUT",
            "/mockserver/bind", portBindingSerializer.serialize(
                portBinding(1080, 1090)
            ));

        // when
        mockServerServlet.service(statusRequest, response);

        // then
        assertResponse(response, 501, "");
    }

    @Test
    public void shouldStop() {
        // given
        MockHttpServletRequest statusRequest = buildHttpServletRequest(
            "PUT",
            "/mockserver/stop",
            ""
        );

        // when
        mockServerServlet.service(statusRequest, response);

        // then
        assertResponse(response, 501, "");
    }

    @Test
    public void shouldRetrieveRecordedExpectations() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_one"))
                .setHttpResponse(response("response_one"))
                .setExpectation(new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited(), 0).withId("key_one").thenRespond(response("response_one")))
        );
        httpStateHandler.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setExpectation(new Expectation(request("request_two"), Times.once(), TimeToLive.unlimited(), 0).withId("key_two").thenRespond(response("response_two")))
        );

        // when
        MockHttpServletRequest expectationRetrieveExpectationsRequest = buildHttpServletRequest(
            "PUT",
            "/mockserver/retrieve",
            httpRequestSerializer.serialize(request("request_one"))
        );
        expectationRetrieveExpectationsRequest.setQueryString("type=" + RetrieveType.RECORDED_EXPECTATIONS.name());
        mockServerServlet.service(expectationRetrieveExpectationsRequest, response);

        // then
        assertResponse(response, 200, expectationSerializer.serialize(Collections.singletonList(
            new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited(), 0).withId("key_one").thenRespond(response("response_one"))
        )));
    }

    @Test
    public void shouldAddExpectation() {
        // given
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        MockHttpServletRequest request = buildHttpServletRequest(
            "PUT",
            "/mockserver/expectation",
            expectationSerializer.serialize(expectationOne)
        );

        // when
        mockServerServlet.service(request, response);

        // then
        assertThat(response.getStatus(), is(201));
        assertThat(new String(response.getContentAsByteArray(), UTF_8), containsString("[ {" + NEW_LINE +
            "  \"id\" : \""));
        assertThat(new String(response.getContentAsByteArray(), UTF_8), containsString("\"," + NEW_LINE +
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
    public void shouldRetrieveActiveExpectations() {
        // given
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.add(expectationOne);
        MockHttpServletRequest expectationRetrieveExpectationsRequest = buildHttpServletRequest(
            "PUT",
            "/mockserver/retrieve",
            httpRequestSerializer.serialize(request("request_one"))
        );
        expectationRetrieveExpectationsRequest.setQueryString("type=" + RetrieveType.ACTIVE_EXPECTATIONS.name());

        // when
        mockServerServlet.service(expectationRetrieveExpectationsRequest, response);

        // then
        assertResponse(response, 200, expectationSerializer.serialize(Collections.singletonList(
            expectationOne
        )));
    }

    @Test
    public void shouldRetrieveLogMessages() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            UUIDService.fixedUUID = true;
            Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
            httpStateHandler.add(expectationOne);
            MockHttpServletRequest retrieveLogRequest = buildHttpServletRequest(
                "PUT",
                "/mockserver/retrieve",
                httpRequestSerializer.serialize(request("request_one"))
            );
            retrieveLogRequest.setQueryString("type=" + RetrieveType.LOGS.name());

            // when
            mockServerServlet.service(retrieveLogRequest, response);

            // then
            assertThat(response.getStatus(), is(200));
            assertThat(new String(response.getContentAsByteArray(), UTF_8), containsString("creating expectation:" + NEW_LINE +
                "" + NEW_LINE +
                "  {" + NEW_LINE +
                "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
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
                "  }" + NEW_LINE));
        } finally {
            UUIDService.fixedUUID = false;
            ConfigurationProperties.logLevel(originalLevel.name());
        }
    }

    @Test
    public void shouldUseActionHandlerToHandleNonAPIRequestsOnDefaultPort() {
        // given
        MockHttpServletRequest request = buildHttpServletRequest(
            "GET",
            "request_one",
            ""
        );
        request.setLocalAddr("local_address");
        request.setLocalPort(80);

        // when
        mockServerServlet.service(request, response);

        // then
        verify(mockActionHandler).processAction(
            eq(
                request("request_one")
                    .withMethod("GET")
                    .withHeader("Content-Type", APPLICATION_JSON_UTF_8.toString())
                    .withKeepAlive(true)
                    .withSecure(false)
            ),
            any(ServletResponseWriter.class),
            isNull(ChannelHandlerContext.class),
            eq(ImmutableSet.of(
                "local_address",
                "localhost",
                "127.0.0.1"
            )),
            eq(false),
            eq(true));
    }

    @Test
    public void shouldUseActionHandlerToHandleNonAPIRequestsOnNonDefaultPort() {
        // given
        MockHttpServletRequest request = buildHttpServletRequest(
            "GET",
            "request_one",
            ""
        );
        request.setLocalAddr("local_address");
        request.setLocalPort(666);

        // when
        mockServerServlet.service(request, response);

        // then
        verify(mockActionHandler).processAction(
            eq(
                request("request_one")
                    .withMethod("GET")
                    .withHeader("Content-Type", APPLICATION_JSON_UTF_8.toString())
                    .withKeepAlive(true)
                    .withSecure(false)
            ),
            any(ServletResponseWriter.class),
            isNull(ChannelHandlerContext.class),
            eq(ImmutableSet.of(
                "local_address:666",
                "localhost:666",
                "127.0.0.1:666"
            )),
            eq(false),
            eq(true));
    }

    @Test
    public void shouldUseActionHandlerToHandleNonAPISecureRequestsOnDefaultPort() {
        // given
        MockHttpServletRequest request = buildHttpServletRequest(
            "GET",
            "request_one",
            ""
        );
        request.setSecure(true);
        request.setLocalAddr("local_address");
        request.setLocalPort(443);

        // when
        mockServerServlet.service(request, response);

        // then
        verify(mockActionHandler).processAction(
            eq(
                request("request_one")
                    .withMethod("GET")
                    .withHeader("Content-Type", APPLICATION_JSON_UTF_8.toString())
                    .withKeepAlive(true)
                    .withSecure(true)
            ),
            any(ServletResponseWriter.class),
            isNull(ChannelHandlerContext.class),
            eq(ImmutableSet.of(
                "local_address",
                "localhost",
                "127.0.0.1"
            )),
            eq(false),
            eq(true));
    }

    @Test
    public void shouldUseActionHandlerToHandleNonAPISecureRequestsOnNonDefaultPort() {
        // given
        MockHttpServletRequest request = buildHttpServletRequest(
            "GET",
            "request_one",
            ""
        );
        request.setSecure(true);
        request.setLocalAddr("local_address");
        request.setLocalPort(666);

        // when
        mockServerServlet.service(request, response);

        // then
        verify(mockActionHandler).processAction(
            eq(
                request("request_one")
                    .withMethod("GET")
                    .withHeader("Content-Type", APPLICATION_JSON_UTF_8.toString())
                    .withKeepAlive(true)
                    .withSecure(true)
            ),
            any(ServletResponseWriter.class),
            isNull(ChannelHandlerContext.class),
            eq(ImmutableSet.of(
                "local_address:666",
                "localhost:666",
                "127.0.0.1:666"
            )),
            eq(false),
            eq(true));
    }

}
