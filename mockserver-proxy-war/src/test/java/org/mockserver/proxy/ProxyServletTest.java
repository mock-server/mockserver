package org.mockserver.proxy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.client.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.RequestLogEntry;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RetrieveType;
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

    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();

    private HttpStateHandler httpStateHandler;
    private NettyHttpClient mockHttpClient;
    private LoggingFormatter mockLogFormatter;

    @InjectMocks
    private ProxyServlet proxyServlet;

    private MockHttpServletResponse response;

    @Before
    public void setupFixture() {
        mockHttpClient = mock(NettyHttpClient.class);
        mockLogFormatter = mock(LoggingFormatter.class);

        httpStateHandler = spy(new HttpStateHandler());
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
            )), is(response().withBody("", JSON_UTF_8).withStatusCode(200)));
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
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.log(new ExpectationMatchLogEntry(
            request("request_one"),
            expectationOne
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
            expectationOne
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
        String[] splitBody = new String(response.getContentAsByteArray(), UTF_8).split("------------------------------------\n");
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
    public void shouldProxyRequests() {
        // given
        HttpRequest request = request("request_one").withHeader("Host", "localhost").withMethod("GET");
        MockHttpServletRequest httpServletRequest = buildHttpServletRequest("GET", "request_one", "");
        httpServletRequest.addHeader("Host", "localhost");
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response("response_one"));

        // when
        proxyServlet.service(httpServletRequest, response);

        // then
        verify(mockHttpClient).sendRequest(request
            .withKeepAlive(true)
            .withSecure(false)
        );
        assertThat(
            httpStateHandler.retrieve(request("/retrieve")
                .withMethod("PUT")
                .withBody(
                    httpRequestSerializer.serialize(request("request_one"))
                )),
            is(response().withBody(httpRequestSerializer.serialize(Collections.singletonList(
                request
            )), JSON_UTF_8).withStatusCode(200))
        );
        verify(mockLogFormatter).infoLog(
            request,
            "returning response:{}" + NEW_LINE + " for request as json:{}" + NEW_LINE + " as curl:{}",
            response("response_one").withHeader("connection", "keep-alive"),
            request,
            new HttpRequestToCurlSerializer().toCurl(request, null)
        );
    }

}
