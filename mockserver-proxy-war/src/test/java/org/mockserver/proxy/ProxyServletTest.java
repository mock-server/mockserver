package org.mockserver.proxy;

import com.google.common.collect.ImmutableSet;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.log.model.RequestLogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RetrieveType;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.server.ServletResponseWriter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static org.apache.commons.codec.Charsets.UTF_8;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class ProxyServletTest {

    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer(new MockServerLogger());
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());

    private HttpStateHandler httpStateHandler;
    private ActionHandler mockActionHandler;
    private MockServerLogger mockLogFormatter;
    private Scheduler scheduler;

    @InjectMocks
    private ProxyServlet proxyServlet;

    private MockHttpServletResponse response;

    @Before
    public void setupFixture() {
        mockActionHandler = mock(ActionHandler.class);
        mockLogFormatter = mock(MockServerLogger.class);
        scheduler = mock(Scheduler.class);

        httpStateHandler = spy(new HttpStateHandler(scheduler));
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
        MockHttpServletRequest expectationRetrieveRequestsRequest = buildHttpServletRequest(
            "PUT",
            "/retrieve",
            httpRequestSerializer.serialize(request("request_one"))
        );
        httpStateHandler.log(new RequestLogEntry(request("request_one")));

        // when
        proxyServlet.service(expectationRetrieveRequestsRequest, response);

        // then
        assertResponse(response, 200, httpRequestSerializer.serialize(Collections.singletonList(
            request("request_one")
        )));
    }

    @Test
    public void shouldClear() {
        // given
        httpStateHandler.add(new Expectation(request("request_one")).thenRespond(response("response_one")));
        httpStateHandler.log(new RequestLogEntry(request("request_one")));
        MockHttpServletRequest clearRequest = buildHttpServletRequest(
            "PUT",
            "/clear",
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
            )), is(response().withBody("[]", JSON_UTF_8).withStatusCode(200)));
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
    public void shouldStop() throws InterruptedException {
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
        httpStateHandler.log(new RequestResponseLogEntry(
            request("request_one"),
            response("response_one")
        ));
        MockHttpServletRequest expectationRetrieveExpectationsRequest = buildHttpServletRequest(
            "PUT",
            "/retrieve",
            httpRequestSerializer.serialize(request("request_one"))
        );
        expectationRetrieveExpectationsRequest.setQueryString("type=" + RetrieveType.RECORDED_EXPECTATIONS.name());

        // when
        proxyServlet.service(expectationRetrieveExpectationsRequest, response);

        // then
        assertResponse(response, 200, expectationSerializer.serialize(Collections.singletonList(
            new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_one"))
        )));
    }

    @Test
    public void shouldRetrieveLogMessages() {
        // given
        MockHttpServletRequest retrieveLogRequest = buildHttpServletRequest(
            "PUT",
            "/retrieve",
            httpRequestSerializer.serialize(request("request_one"))
        );
        retrieveLogRequest.setQueryString("type=" + RetrieveType.LOGS.name());

        // when
        proxyServlet.service(retrieveLogRequest, response);

        // then
        assertThat(response.getStatus(), is(200));
        String[] splitBody = new String(response.getContentAsByteArray(), UTF_8).split("\n------------------------------------\n");
        assertThat(splitBody.length, is(1));
        assertThat(
            splitBody[0],
            is(endsWith("retrieving logs that match:" + NEW_LINE +
                NEW_LINE +
                "\t{" + NEW_LINE +
                "\t  \"path\" : \"request_one\"" + NEW_LINE +
                "\t}" + NEW_LINE +
                NEW_LINE))
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
