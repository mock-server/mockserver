package org.mockserver.mock.action.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import org.junit.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.time.EpochService;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.log.model.LogEntryMessages.RECEIVED_REQUEST_MESSAGE_FORMAT;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
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
public class HttpActionHandlerTest {

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
    private HttpState mockHttpStateHandler;
    private HttpRequest request;
    private HttpResponse response;
    private CompletableFuture<HttpResponse> responseFuture;
    private HttpRequest forwardedHttpRequest;
    private HttpForwardActionResult httpForwardActionResult;
    private Expectation expectation;
    @InjectMocks
    private HttpActionHandler actionHandler;
    private Level originalLogLevel;

    @BeforeClass
    public static void fixTime() {
        EpochService.fixedTime = true;
    }

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Before
    public void setupMocks() {
        originalLogLevel = ConfigurationProperties.logLevel();
        ConfigurationProperties.logLevel("INFO");

        mockHttpStateHandler = mock(HttpState.class);
        scheduler = spy(new Scheduler(configuration(), mockServerLogger));
        when(mockHttpStateHandler.getScheduler()).thenReturn(scheduler);
        when(mockHttpStateHandler.getUniqueLoopPreventionHeaderValue()).thenReturn("MockServer_" + UUIDService.getUUID());
        actionHandler = new HttpActionHandler(configuration(), null, mockHttpStateHandler, null, null);

        openMocks(this);
        request = request("some_path");
        response = response("some_body").withDelay(milliseconds(0));
        responseFuture = new CompletableFuture<>();
        responseFuture.complete(response);
        forwardedHttpRequest = mock(HttpRequest.class);
        httpForwardActionResult = new HttpForwardActionResult(forwardedHttpRequest, responseFuture, null, new InetSocketAddress(1234));
        expectation = new Expectation(request).thenRespond(response);

        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        when(mockHttpResponseActionHandler.handle(any(HttpResponse.class))).thenReturn(response);
        when(mockHttpResponseTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenReturn(response);
        when(mockHttpResponseClassCallbackActionHandler.handle(any(HttpClassCallback.class), any(HttpRequest.class))).thenReturn(response);
        when(mockHttpForwardActionHandler.handle(any(HttpForward.class), any(HttpRequest.class))).thenReturn(httpForwardActionResult);
        when(mockHttpForwardTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenReturn(httpForwardActionResult);
        when(mockHttpForwardClassCallbackActionHandler.handle(any(HttpClassCallback.class), any(HttpRequest.class))).thenReturn(httpForwardActionResult);
        when(mockHttpOverrideForwardedRequestActionHandler.handle(any(HttpOverrideForwardedRequest.class), any(HttpRequest.class))).thenReturn(httpForwardActionResult);
    }

    @After
    public void resetLogLevel() {
        ConfigurationProperties.logLevel(originalLogLevel.name());
    }

    @Test
    public void shouldProcessResponseAction() {
        // given
        HttpResponse response = response("some_body").withDelay(milliseconds(1));
        expectation = new Expectation(request).thenRespond(response);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request)
                .setHttpResponse(this.response)
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning response:{}for request:{}for action:{}from expectation:{}")
                .setArguments(this.response, request, response, expectation.getId())
        );
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(0)));
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(0)));
    }

    @Test
    public void shouldProcessResponseTemplateAction() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template").withDelay(milliseconds(1));
        expectation = new Expectation(request).thenRespond(template);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning response:{}for request:{}for action:{}from expectation:{}")
                .setArguments(response, request, template, expectation.getId())
        );
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(1)));
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(0)));
    }

    @Test
    public void shouldHandleResponseTemplateActionException() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template").withDelay(milliseconds(1));
        expectation = new Expectation(request).thenRespond(template);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setHttpResponse(notFoundResponse())
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning response:{}for request:{}for action:{}from expectation:{}")
                .setArguments(notFoundResponse(), request, expectation.getAction(), expectation.getId())
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
        expectation = new Expectation(request).thenRespond(callback);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning response:{}for request:{}for action:{}from expectation:{}")
                .setArguments(response, request, callback, expectation.getId())
        );
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(1)));
        verify(scheduler).schedule(any(Runnable.class), eq(true), eq(milliseconds(0)));
    }

    @Test
    public void shouldProcessResponseObjectCallbackAction() {
        // given
        HttpObjectCallback callback = new HttpObjectCallback().withClientId("some_request_client_id").withDelay(milliseconds(1));
        expectation = new Expectation(request).thenRespond(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpResponseObjectCallbackActionHandler).handle(any(HttpActionHandler.class), same(callback), same(request), same(mockResponseWriter), eq(true), any(Runnable.class));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
    }

    @Test
    public void shouldProcessForwardAction() {
        // given
        HttpForward forward = forward()
            .withHost("localhost")
            .withPort(1090);
        expectation = new Expectation(request).thenForward(forward);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectation(request, response)
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning response:{}for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}" + NEW_LINE + NEW_LINE + " in curl:{}for action:{}from expectation:{}")
                .setArguments(response, forwardedHttpRequest, "curl -v 'http://" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "/'", expectation.getAction(), expectation.getId())
        );
        verify(httpRequestToCurlSerializer).toCurl(forwardedHttpRequest, remoteAddress);
    }

    @Test
    public void shouldProcessForwardTemplateAction() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template");
        expectation = new Expectation(request).thenForward(template);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectation(request, response)
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning response:{}for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}" + NEW_LINE + NEW_LINE + " in curl:{}for action:{}from expectation:{}")
                .setArguments(response, forwardedHttpRequest, "curl -v 'http://" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "/'", expectation.getAction(), expectation.getId())
        );
        verify(httpRequestToCurlSerializer).toCurl(forwardedHttpRequest, remoteAddress);
    }

    @Test
    public void shouldHandleForwardTemplateActionException() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template");
        expectation = new Expectation(request).thenForward(template);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setHttpResponse(notFoundResponse())
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning response:{}for request:{}for action:{}from expectation:{}")
                .setArguments(notFoundResponse(), request, expectation.getAction(), expectation.getId())
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
        expectation = new Expectation(request).thenForward(callback);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectation(request, response)
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning response:{}for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}" + NEW_LINE + NEW_LINE + " in curl:{}for action:{}from expectation:{}")
                .setArguments(response, forwardedHttpRequest, "curl -v 'http://" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "/'", expectation.getAction(), expectation.getId())
        );
        verify(httpRequestToCurlSerializer).toCurl(forwardedHttpRequest, remoteAddress);
    }

    @Test
    public void shouldProcessForwardObjectCallbackAction() {
        // given
        HttpObjectCallback callback = new HttpObjectCallback().withClientId("some_forward_client_id");
        expectation = new Expectation(request).thenForward(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        verify(mockHttpForwardObjectCallbackActionHandler).handle(any(HttpActionHandler.class), same(callback), same(request), same(mockResponseWriter), eq(true), any(Runnable.class));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
    }

    @Test
    public void shouldProcessOverrideForwardedRequest() {
        // given
        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest().withRequestOverride(request("some_overridden_path"));
        expectation = new Expectation(request).thenForward(httpOverrideForwardedRequest);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request)
                .setHttpResponse(response)
                .setExpectation(request, response)
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning response:{}for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}" + NEW_LINE + NEW_LINE + " in curl:{}for action:{}from expectation:{}")
                .setArguments(response, forwardedHttpRequest, "curl -v 'http://" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "/'", expectation.getAction(), expectation.getId())
        );
        verify(httpRequestToCurlSerializer).toCurl(forwardedHttpRequest, remoteAddress);
    }

    @Test
    public void shouldProcessErrorAction() {
        // given
        HttpError error = error();
        expectation = new Expectation(request).thenError(error);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request)
        );
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request)
                .setHttpError(error)
                .setExpectationId(expectation.getAction().getExpectationId())
                .setMessageFormat("returning error:{}for request:{}for action:{}from expectation:{}")
                .setArguments(error, request, error, expectation.getId())
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
        InetSocketAddress remoteAddress = new InetSocketAddress(1090);
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
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
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
