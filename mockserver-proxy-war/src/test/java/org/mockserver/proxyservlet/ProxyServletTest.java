package org.mockserver.proxyservlet;

import com.google.common.collect.ImmutableSet;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.TimeService;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;
import org.mockserver.model.RetrieveType;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.PortBindingSerializer;
import org.mockserver.servlet.responsewriter.ServletResponseWriter;
import org.slf4j.event.Level;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.Date;

import static org.apache.commons.codec.Charsets.UTF_8;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LOG_DATE_FORMAT;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class ProxyServletTest {

    private final HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer(new MockServerLogger());
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());
    private final PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());

    private HttpStateHandler httpStateHandler;
    private ActionHandler mockActionHandler;

    @InjectMocks
    private ProxyServlet proxyServlet;

    private MockHttpServletResponse response;

    @BeforeClass
    public static void fixTime() {
        TimeService.fixedTime = true;
    }

    @Before
    public void setupFixture() {
        mockActionHandler = mock(ActionHandler.class);
        Scheduler scheduler = mock(Scheduler.class);

        httpStateHandler = spy(new HttpStateHandler(new MockServerLogger(), scheduler));
        response = new MockHttpServletResponse();
        proxyServlet = new ProxyServlet();


        initMocks(this);
    }

    private MockHttpServletRequest buildHttpServletRequest(String method, String requestURI, String body) {
        MockHttpServletRequest expectationRetrieveRequestsRequest = new MockHttpServletRequest(method, requestURI);
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
                .setHttpRequest(request("request_one"))
                .setType(RECEIVED_REQUEST)
        );
        // when
        proxyServlet
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
        proxyServlet.service(clearRequest, response);

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
            "/status",
            ""
        );

        // when
        proxyServlet.service(statusRequest, response);

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
            proxyServlet.service(statusRequest, response);

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
            "/bind", portBindingSerializer.serialize(
                portBinding(1080, 1090)
            ));

        // when
        proxyServlet.service(statusRequest, response);

        // then
        assertResponse(response, 501, "");
    }

    @Test
    public void shouldStop() {
        // given
        MockHttpServletRequest statusRequest = buildHttpServletRequest(
            "PUT",
            "/stop",
            ""
        );

        // when
        proxyServlet.service(statusRequest, response);

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
        // when
        MockHttpServletRequest expectationRetrieveExpectationsRequest = buildHttpServletRequest(
            "PUT",
            "/mockserver/retrieve",
            httpRequestSerializer.serialize(request("request_one"))
        );
        expectationRetrieveExpectationsRequest.setQueryString("type=" + RetrieveType.RECORDED_EXPECTATIONS.name());
        proxyServlet.service(expectationRetrieveExpectationsRequest, response);

        // then
        assertResponse(response, 200, expectationSerializer.serialize(Collections.singletonList(
            new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited(), 0).withId("key_one").thenRespond(response("response_one"))
        )));
    }

    @Test
    public void shouldRetrieveLogMessages() {
        // given
        httpStateHandler.log(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request("request_one"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_one"))
        );
        // when
        MockHttpServletRequest retrieveLogRequest = buildHttpServletRequest(
            "PUT",
            "/mockserver/retrieve",
            httpRequestSerializer.serialize(request("request_one"))
        );
        retrieveLogRequest.setQueryString("type=" + RetrieveType.LOGS.name());
        proxyServlet.service(retrieveLogRequest, response);

        // then
        assertThat(response.getStatus(), is(200));
        assertThat(
            new String(response.getContentAsByteArray(), UTF_8),
            is(endsWith(LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - received request:" + NEW_LINE +
                "" + NEW_LINE +
                "\t{" + NEW_LINE +
                "\t  \"path\" : \"request_one\"" + NEW_LINE +
                "\t}" + NEW_LINE +
                "" + NEW_LINE +
                "------------------------------------" + NEW_LINE +
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                "" + NEW_LINE +
                "\t{" + NEW_LINE +
                "\t  \"path\" : \"request_one\"" + NEW_LINE +
                "\t}" + NEW_LINE +
                "" + NEW_LINE))
        );
    }

    @Test
    public void shouldProxyRequestsOnDefaultPort() {
        // given
        HttpRequest request = request("request_one").withHeader("Host", "localhost").withMethod("GET");
        MockHttpServletRequest httpServletRequest = buildHttpServletRequest("GET", "request_one", "");
        httpServletRequest.addHeader("Host", "localhost");
        httpServletRequest.setLocalAddr("local_address");
        httpServletRequest.setLocalPort(80);

        // when
        proxyServlet.service(httpServletRequest, response);

        // then
        verify(mockActionHandler).processAction(
            eq(
                request
                    .withSecure(false)
                    .withKeepAlive(true)
            ),
            any(ServletResponseWriter.class),
            isNull(ChannelHandlerContext.class),
            eq(ImmutableSet.of(
                "local_address",
                "localhost",
                "127.0.0.1"
            )),
            eq(true),
            eq(true));
    }

    @Test
    public void shouldProxyRequestsOnNonDefaultPort() {
        // given
        HttpRequest request = request("request_one").withHeader("Host", "localhost").withMethod("GET");
        MockHttpServletRequest httpServletRequest = buildHttpServletRequest("GET", "request_one", "");
        httpServletRequest.addHeader("Host", "localhost");
        httpServletRequest.setLocalAddr("local_address");
        httpServletRequest.setLocalPort(666);

        // when
        proxyServlet.service(httpServletRequest, response);

        // then
        verify(mockActionHandler).processAction(
            eq(
                request
                    .withSecure(false)
                    .withKeepAlive(true)
            ),
            any(ServletResponseWriter.class),
            isNull(ChannelHandlerContext.class),
            eq(ImmutableSet.of(
                "local_address:666",
                "localhost:666",
                "127.0.0.1:666"
            )),
            eq(true),
            eq(true));
    }

    @Test
    public void shouldProxySecureRequestsOnDefaultPort() {
        // given
        HttpRequest request = request("request_one").withHeader("Host", "localhost").withMethod("GET");
        MockHttpServletRequest httpServletRequest = buildHttpServletRequest("GET", "request_one", "");
        httpServletRequest.addHeader("Host", "localhost");
        httpServletRequest.setSecure(true);
        httpServletRequest.setLocalAddr("local_address");
        httpServletRequest.setLocalPort(443);

        // when
        proxyServlet.service(httpServletRequest, response);

        // then
        verify(mockActionHandler).processAction(
            eq(
                request
                    .withSecure(true)
                    .withKeepAlive(true)
            ),
            any(ServletResponseWriter.class),
            isNull(ChannelHandlerContext.class),
            eq(ImmutableSet.of(
                "local_address",
                "localhost",
                "127.0.0.1"
            )),
            eq(true),
            eq(true));
    }

    @Test
    public void shouldProxySecureRequestsOnNonDefaultPort() {
        // given
        HttpRequest request = request("request_one").withHeader("Host", "localhost").withMethod("GET");
        MockHttpServletRequest httpServletRequest = buildHttpServletRequest("GET", "request_one", "");
        httpServletRequest.addHeader("Host", "localhost");
        httpServletRequest.setSecure(true);
        httpServletRequest.setLocalAddr("local_address");
        httpServletRequest.setLocalPort(666);

        // when
        proxyServlet.service(httpServletRequest, response);

        // then
        verify(mockActionHandler).processAction(
            eq(
                request
                    .withSecure(true)
                    .withKeepAlive(true)
            ),
            any(ServletResponseWriter.class),
            isNull(ChannelHandlerContext.class),
            eq(ImmutableSet.of(
                "local_address:666",
                "localhost:666",
                "127.0.0.1:666"
            )),
            eq(true),
            eq(true));
    }

}
