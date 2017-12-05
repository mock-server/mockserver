package org.mockserver.proxy.http;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RetrieveType;
import org.mockserver.proxy.Proxy;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.proxy.Proxy.REMOTE_SOCKET;

/**
 * @author jamesdbloom
 */
public class HttpProxyHandlerTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();

    private HttpStateHandler httpStateHandler;
    private Proxy mockProxy;
    private NettyHttpClient mockHttpClient;
    private LoggingFormatter mockLogFormatter;

    @InjectMocks
    private HttpProxyHandler httpProxyHandler;

    private EmbeddedChannel embeddedChannel;

    @Before
    public void setupFixture() {
        mockProxy = mock(Proxy.class);
        mockHttpClient = mock(NettyHttpClient.class);
        mockLogFormatter = mock(LoggingFormatter.class);

        httpStateHandler = new HttpStateHandler();
        httpProxyHandler = new HttpProxyHandler(mockProxy, httpStateHandler);

        initMocks(this);

        embeddedChannel = new EmbeddedChannel(httpProxyHandler);
    }

    private void assertResponse(int responseStatusCode, String responseBody) {
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(responseStatusCode));
        assertThat(httpResponse.getBodyAsString(), is(responseBody));
    }

    @Test
    public void shouldRetrieveRequests() {
        // given
        httpStateHandler.log(new RequestLogEntry(request("request_one")));
        HttpRequest expectationRetrieveRequestsRequest = request("/retrieve")
                .withMethod("PUT")
                .withBody(
                        httpRequestSerializer.serialize(request("request_one"))
                );

        // when
        embeddedChannel.writeInbound(expectationRetrieveRequestsRequest);

        // then
        assertResponse(200, httpRequestSerializer.serialize(Collections.singletonList(
                request("request_one")
        )));
    }

    @Test
    public void shouldClear() {
        // given
        httpStateHandler.add(new Expectation(request("request_one")).thenRespond(response("response_one")));
        httpStateHandler.log(new RequestLogEntry(request("request_one")));
        HttpRequest clearRequest = request("/clear")
                .withMethod("PUT")
                .withBody(
                        httpRequestSerializer.serialize(request("request_one"))
                );

        // when
        embeddedChannel.writeInbound(clearRequest);

        // then
        assertResponse(200, "");
        assertThat(httpStateHandler.firstMatchingExpectation(request("request_one")), is(nullValue()));
        assertThat(httpStateHandler.retrieve(request("/retrieve")
                .withMethod("PUT")
                .withBody(
                        httpRequestSerializer.serialize(request("request_one"))
                )), is(""));
    }

    @Test
    public void shouldReturnStatus() {
        // given
        when(mockProxy.getPorts()).thenReturn(Arrays.asList(1080, 1090));
        HttpRequest statusRequest = request("/status").withMethod("PUT");

        // when
        embeddedChannel.writeInbound(statusRequest);

        // then
        assertResponse(200, portBindingSerializer.serialize(
                portBinding(1080, 1090)
        ));
    }

    @Test
    public void shouldBindNewPorts() {
        // given
        when(mockProxy.bindToPorts(anyListOf(Integer.class))).thenReturn(Arrays.asList(1080, 1090));
        HttpRequest statusRequest = request("/bind")
                .withMethod("PUT")
                .withBody(portBindingSerializer.serialize(
                        portBinding(1080, 1090)
                ));

        // when
        embeddedChannel.writeInbound(statusRequest);

        // then
        verify(mockProxy).bindToPorts(Arrays.asList(1080, 1090));
        assertResponse(200, portBindingSerializer.serialize(
                portBinding(1080, 1090)
        ));
    }

    @Test
    public void shouldStop() throws InterruptedException {
        // given
        HttpRequest statusRequest = request("/stop")
                .withMethod("PUT");

        // when
        embeddedChannel.writeInbound(statusRequest);

        // then
        assertResponse(200, null);
        TimeUnit.SECONDS.sleep(1); // ensure stop thread has run
        verify(mockProxy).stop();
    }

    @Test
    public void shouldRetrieveRecordedExpectations() {
        // given
        Expectation expectationOne = new Expectation(request("request_one")).thenRespond(response("response_one"));
        httpStateHandler.log(new ExpectationMatchLogEntry(
                request("request_one"),
                expectationOne
        ));
        HttpRequest expectationRetrieveExpectationsRequest = request("/retrieve")
                .withMethod("PUT")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withBody(
                        httpRequestSerializer.serialize(request("request_one"))
                );

        // when
        embeddedChannel.writeInbound(expectationRetrieveExpectationsRequest);

        // then
        assertResponse(200, expectationSerializer.serialize(Collections.singletonList(
                expectationOne
        )));
    }

    @Test
    public void shouldProxyRequests() {
        // given
        HttpRequest request = request("request_one");
        InetSocketAddress remoteAddress = new InetSocketAddress(1080);
        when(mockHttpClient.sendRequest(request, remoteAddress)).thenReturn(response("response_one"));

        // when
        embeddedChannel.attr(REMOTE_SOCKET).set(remoteAddress);
        embeddedChannel.writeInbound(request);

        // then
        verify(mockHttpClient).sendRequest(request, remoteAddress);
        assertThat(
                httpStateHandler.retrieve(request("/retrieve")
                        .withMethod("PUT")
                        .withBody(
                                httpRequestSerializer.serialize(request("request_one"))
                        )),
                is(httpRequestSerializer.serialize(Collections.singletonList(
                        request("request_one")
                )))
        );
        verify(mockLogFormatter).infoLog(
                "returning response:{}" + NEW_LINE + " for request as json:{}" + NEW_LINE + " as curl:{}",
                response("response_one").withHeader("connection", "close"),
                request,
                new HttpRequestToCurlSerializer().toCurl(request, remoteAddress)
        );
    }

}
