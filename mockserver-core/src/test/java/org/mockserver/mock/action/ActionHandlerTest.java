package org.mockserver.mock.action;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.TimeService;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.curl.HttpRequestToCurlSerializer;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.mock.action.ActionHandler.REMOTE_SOCKET;
import static org.mockserver.model.Delay.milliseconds;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.slf4j.event.Level.INFO;

/**
 * @author jamesdbloom
 */
public class ActionHandlerTest {

    private static Scheduler scheduler;
    @Mock
    private HttpResponseActionHandler mockHttpResponseActionHandler;
    @Mock
    private HttpResponseTemplateActionHandler mockHttpResponseTemplateActionHandler;
    @Mock
    private HttpResponseClassCallbackActionHandler mockHttpResponseClassCallbackActionHandler;
    @Mock
    private HttpResponseObjectCallbackActionHandler mockHttpResponseObjectCallbackActionHandler;
    @Mock
    private HttpForwardActionHandler mockHttpForwardActionHandler;
    @Mock
    private HttpForwardTemplateActionHandler mockHttpForwardTemplateActionHandler;
    @Mock
    private HttpForwardClassCallbackActionHandler mockHttpForwardClassCallbackActionHandler;
    @Mock
    private HttpForwardObjectCallbackActionHandler mockHttpForwardObjectCallbackActionHandler;
    @Mock
    private HttpOverrideForwardedRequestActionHandler mockHttpOverrideForwardedRequestActionHandler;
    @Mock
    private HttpErrorActionHandler mockHttpErrorActionHandler;
    @Mock
    private ResponseWriter mockResponseWriter;
    @Mock
    private MockServerLogger mockServerLogger;
    @Spy
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);
    @Mock
    private NettyHttpClient mockNettyHttpClient;
    private HttpStateHandler mockHttpStateHandler;
    private HttpRequest request;
    private HttpResponse response;
    private CompletableFuture<HttpResponse> responseFuture;
    private HttpRequest forwardedHttpRequest;
    private HttpForwardActionResult httpForwardActionResult;
    private Expectation expectation;
    @InjectMocks
    private ActionHandler actionHandler;

    @BeforeClass
    public static void fixTime() {
        TimeService.fixedTime = true;
    }

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Before
    public void setupMocks() {
        mockHttpStateHandler = mock(HttpStateHandler.class);
        scheduler = spy(new Scheduler(mockServerLogger));
        when(mockHttpStateHandler.getScheduler()).thenReturn(scheduler);
        when(mockHttpStateHandler.getUniqueLoopPreventionHeaderValue()).thenReturn("MockServer_" + UUID.randomUUID().toString());
        actionHandler = new ActionHandler(null, mockHttpStateHandler, null, null);

        initMocks(this);
        request = request("some_path");
        response = response("some_body").withDelay(milliseconds(0));
        responseFuture = new CompletableFuture<>();
        responseFuture.complete(response);
        forwardedHttpRequest = mock(HttpRequest.class);
        httpForwardActionResult = new HttpForwardActionResult(forwardedHttpRequest, responseFuture, null, new InetSocketAddress(1234));
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(response);

        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        when(mockHttpResponseActionHandler.handle(any(HttpResponse.class))).thenReturn(response);
        when(mockHttpResponseTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenReturn(response);
        when(mockHttpResponseClassCallbackActionHandler.handle(any(HttpClassCallback.class), any(HttpRequest.class))).thenReturn(response);
        when(mockHttpForwardActionHandler.handle(any(HttpForward.class), any(HttpRequest.class))).thenReturn(httpForwardActionResult);
        when(mockHttpForwardTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenReturn(httpForwardActionResult);
        when(mockHttpForwardClassCallbackActionHandler.handle(any(HttpClassCallback.class), any(HttpRequest.class))).thenReturn(httpForwardActionResult);
        when(mockHttpOverrideForwardedRequestActionHandler.handle(any(HttpOverrideForwardedRequest.class), any(HttpRequest.class))).thenReturn(httpForwardActionResult);
    }

    @Test
    public void shouldProcessResponseAction() {
        // given
        HttpResponse response = response("some_template").withDelay(milliseconds(1));
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(response);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpResponseActionHandler).handle(response);
        verify(mockResponseWriter).writeResponse(request, this.response, false);
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request)
                .setHttpResponse(this.response)
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(this.response, request, response)
        );
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(1)));
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(0)));
    }

    @Test
    public void shouldProcessResponseTemplateAction() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template").withDelay(milliseconds(1));
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(template);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpResponseTemplateActionHandler).handle(template, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(response, request, template)
        );
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(1)));
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(0)));
    }

    @Test
    public void shouldHandleResponseTemplateActionException() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template").withDelay(milliseconds(1));
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(template);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        RuntimeException throwable = new RuntimeException("TEST_EXCEPTION");
        when(mockHttpResponseTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenThrow(throwable);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpResponseTemplateActionHandler).handle(template, request);
        verify(mockResponseWriter).writeResponse(request, notFoundResponse(), false);
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setHttpResponse(notFoundResponse())
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(notFoundResponse(), request, expectation.getAction())
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(WARN)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat(throwable.getMessage())
                .setThrowable(throwable)
        );
    }

    @Test
    public void shouldProcessResponseClassCallbackAction() {
        // given
        HttpClassCallback callback = callback("some_class").withDelay(milliseconds(1));
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpResponseClassCallbackActionHandler).handle(callback, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(response, request, callback)
        );
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(1)));
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(0)));
    }

    @Test
    public void shouldProcessResponseObjectCallbackAction() {
        // given
        HttpObjectCallback callback = new HttpObjectCallback().withClientId("some_request_client_id").withDelay(milliseconds(1));
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpResponseObjectCallbackActionHandler).handle(any(ActionHandler.class), same(callback), same(request), same(mockResponseWriter), eq(true), any(Runnable.class));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
    }

    @Test
    public void shouldProcessForwardAction() {
        // given
        HttpForward forward = forward()
            .withHost("localhost")
            .withPort(1080);
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(forward);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpForwardActionHandler).handle(forward, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        InetSocketAddress remoteAddress = httpForwardActionResult.getRemoteAddress();
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectation(request, response)
                .setMessageFormat("returning response:{}for forwarded request\n\n in json:{}\n\n in curl:{}for action:{}")
                .setArguments(response, forwardedHttpRequest, "curl -v 'http://" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "/'", expectation.getAction())
        );
        verify(httpRequestToCurlSerializer).toCurl(forwardedHttpRequest, remoteAddress);
    }

    @Test
    public void shouldProcessForwardTemplateAction() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(template);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpForwardTemplateActionHandler).handle(template, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        InetSocketAddress remoteAddress = httpForwardActionResult.getRemoteAddress();
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectation(request, response)
                .setMessageFormat("returning response:{}for forwarded request\n\n in json:{}\n\n in curl:{}for action:{}")
                .setArguments(response, forwardedHttpRequest, "curl -v 'http://" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "/'", expectation.getAction())
        );
        verify(httpRequestToCurlSerializer).toCurl(forwardedHttpRequest, remoteAddress);
    }

    @Test
    public void shouldHandleForwardTemplateActionException() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(template);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        RuntimeException throwable = new RuntimeException("TEST_EXCEPTION");
        when(mockHttpForwardTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenThrow(throwable);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpForwardTemplateActionHandler).handle(template, request);
        verify(mockResponseWriter).writeResponse(request, notFoundResponse(), false);
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setHttpResponse(notFoundResponse())
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(notFoundResponse(), request, expectation.getAction())
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(WARN)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat(throwable.getMessage())
                .setThrowable(throwable)
        );
    }

    @Test
    public void shouldProcessForwardClassCallbackAction() {
        // given
        HttpClassCallback callback = callback("some_class");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpForwardClassCallbackActionHandler).handle(callback, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        InetSocketAddress remoteAddress = httpForwardActionResult.getRemoteAddress();
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectation(request, response)
                .setMessageFormat("returning response:{}for forwarded request\n\n in json:{}\n\n in curl:{}for action:{}")
                .setArguments(response, forwardedHttpRequest, "curl -v 'http://" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "/'", expectation.getAction())
        );
        verify(httpRequestToCurlSerializer).toCurl(forwardedHttpRequest, remoteAddress);
    }

    @Test
    public void shouldProcessForwardObjectCallbackAction() {
        // given
        HttpObjectCallback callback = new HttpObjectCallback().withClientId("some_forward_client_id");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpForwardObjectCallbackActionHandler).handle(any(ActionHandler.class), same(callback), same(request), same(mockResponseWriter), eq(true), any(Runnable.class));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
    }

    @Test
    public void shouldProcessOverrideForwardedRequest() {
        // given
        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest().withHttpRequest(request("some_overridden_path"));
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(httpOverrideForwardedRequest);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpOverrideForwardedRequestActionHandler).handle(httpOverrideForwardedRequest, request);
        InetSocketAddress remoteAddress = httpForwardActionResult.getRemoteAddress();
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectation(request, response)
                .setMessageFormat("returning response:{}for forwarded request\n\n in json:{}\n\n in curl:{}for action:{}")
                .setArguments(response, forwardedHttpRequest, "curl -v 'http://" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "/'", expectation.getAction())
        );
        verify(httpRequestToCurlSerializer).toCurl(forwardedHttpRequest, remoteAddress);
    }

    @Test
    public void shouldProcessErrorAction() {
        // given
        HttpError error = error();
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenError(error);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);
        ChannelHandlerContext mockChannelHandlerContext = mock(ChannelHandlerContext.class);

        // when
        actionHandler.processAction(request, mockResponseWriter, mockChannelHandlerContext, new HashSet<>(), false, true);

        // then
        verify(mockHttpErrorActionHandler).handle(error, mockChannelHandlerContext);
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request)
                .setHttpError(error)
                .setMessageFormat("returning error:{}for request:{}for action:{}")
                .setArguments(error, request, error)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldProxyRequestsWithRemoteSocketAttribute() {
        // given
        HttpRequest request = request("request_one");

        // and - remote socket attribute
        ChannelHandlerContext mockChannelHandlerContext = mock(ChannelHandlerContext.class);
        Channel mockChannel = mock(Channel.class);
        when(mockChannelHandlerContext.channel()).thenReturn(mockChannel);
        InetSocketAddress remoteAddress = new InetSocketAddress(1080);
        Attribute<InetSocketAddress> inetSocketAddressAttribute = mock(Attribute.class);
        when(inetSocketAddressAttribute.get()).thenReturn(remoteAddress);
        when(mockChannel.attr(REMOTE_SOCKET)).thenReturn(inetSocketAddressAttribute);

        // and - netty http client
        HttpRequest requestBeingForwarded = request("request_one").withHeader(mockHttpStateHandler.getUniqueLoopPreventionHeaderName(), mockHttpStateHandler.getUniqueLoopPreventionHeaderValue());
        when(mockNettyHttpClient.sendRequest(requestBeingForwarded, remoteAddress, ConfigurationProperties.socketConnectionTimeout())).thenReturn(responseFuture);

        // when
        actionHandler.processAction(request("request_one"), mockResponseWriter, mockChannelHandlerContext, new HashSet<>(), true, true);

        // then
        verify(mockNettyHttpClient).sendRequest(requestBeingForwarded, remoteAddress, ConfigurationProperties.socketConnectionTimeout());
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectation(request, response)
                .setMessageFormat("returning response:{}for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}" + NEW_LINE + NEW_LINE + " in curl:{}")
                .setArguments(response, request, httpRequestToCurlSerializer.toCurl(request, remoteAddress))
        );
    }
}
