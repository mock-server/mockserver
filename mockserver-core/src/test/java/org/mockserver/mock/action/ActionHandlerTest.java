package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import java.net.InetSocketAddress;
import java.util.HashSet;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.mock.action.ActionHandler.REMOTE_SOCKET;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class ActionHandlerTest {

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
    private MockServerLogger mockLogFormatter;

    @Mock
    private NettyHttpClient mockNettyHttpClient;

    private HttpStateHandler mockHttpStateHandler;
    private HttpRequest request;
    private HttpResponse response;
    private SettableFuture<HttpResponse> responseFuture;
    private Expectation expectation;
    private static Scheduler scheduler = new Scheduler();

    @InjectMocks
    private ActionHandler actionHandler;

    @Before
    public void setupMocks() {
        mockHttpStateHandler = mock(HttpStateHandler.class);
        when(mockHttpStateHandler.getScheduler()).thenReturn(scheduler);
        actionHandler = new ActionHandler(mockHttpStateHandler, null);

        initMocks(this);
        request = request("some_path");
        response = response("some_body");
        responseFuture = SettableFuture.create();
        responseFuture.set(response);
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(response);

        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        when(mockHttpResponseActionHandler.handle(any(HttpResponse.class))).thenReturn(response);
        when(mockHttpResponseTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenReturn(response);
        when(mockHttpResponseClassCallbackActionHandler.handle(any(HttpClassCallback.class), any(HttpRequest.class))).thenReturn(response);
        when(mockHttpForwardActionHandler.handle(any(HttpForward.class), any(HttpRequest.class))).thenReturn(responseFuture);
        when(mockHttpForwardTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenReturn(responseFuture);
        when(mockHttpForwardClassCallbackActionHandler.handle(any(HttpClassCallback.class), any(HttpRequest.class))).thenReturn(responseFuture);
        when(mockHttpOverrideForwardedRequestActionHandler.handle(any(HttpOverrideForwardedRequest.class), any(HttpRequest.class))).thenReturn(responseFuture);
    }

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Test
    public void shouldProcessResponseAction() {
        // given
        HttpResponse response = response("some_template");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(response);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<String>(), false, true);

        // then
        verify(mockHttpResponseActionHandler).handle(response);
        verify(mockResponseWriter).writeResponse(request, this.response, false);
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(mockLogFormatter).info(EXPECTATION_RESPONSE, request, "returning response:{}for request:{}for action:{}", this.response, request, response);
    }

    @Test
    public void shouldProcessResponseTemplateAction() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(template);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<String>(), false, true);

        // then
        verify(mockHttpResponseTemplateActionHandler).handle(template, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(mockLogFormatter).info(EXPECTATION_RESPONSE, request, "returning response:{}for request:{}for action:{}", response, request, template);
    }

    @Test
    public void shouldProcessResponseClassCallbackAction() {
        // given
        HttpClassCallback callback = callback("some_class");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<String>(), false, true);

        // then
        verify(mockHttpResponseClassCallbackActionHandler).handle(callback, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(mockLogFormatter).info(EXPECTATION_RESPONSE, request, "returning response:{}for request:{}for action:{}", response, request, callback);
    }

    @Test
    public void shouldProcessResponseObjectCallbackAction() {
        // given
        HttpObjectCallback callback = new HttpObjectCallback().withClientId("some_request_client_id");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<String>(), false, true);

        // then
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(mockHttpResponseObjectCallbackActionHandler).handle(callback, request, mockResponseWriter);
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
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<String>(), false, true);

        // then
        verify(mockHttpForwardActionHandler).handle(forward, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        verify(mockHttpStateHandler, times(1)).log(new RequestResponseLogEntry(request, response));
        verify(mockLogFormatter).info(EXPECTATION_RESPONSE, request, "returning response:{}for request:{}for action:{}", response, request, forward);
    }

    @Test
    public void shouldProcessForwardTemplateAction() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(template);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<String>(), false, true);

        // then
        verify(mockHttpForwardTemplateActionHandler).handle(template, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        verify(mockHttpStateHandler, times(1)).log(new RequestResponseLogEntry(request, response));
        verify(mockLogFormatter).info(EXPECTATION_RESPONSE, request, "returning response:{}for request:{}for action:{}", response, request, template);
    }

    @Test
    public void shouldProcessForwardClassCallbackAction() {
        // given
        HttpClassCallback callback = callback("some_class");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<String>(), false, true);

        // then
        verify(mockHttpForwardClassCallbackActionHandler).handle(callback, request);
        verify(mockResponseWriter).writeResponse(request, response, false);
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(mockLogFormatter).info(EXPECTATION_RESPONSE, request, "returning response:{}for request:{}for action:{}", response, request, callback);
    }

    @Test
    public void shouldProcessForwardObjectCallbackAction() {
        // given
        HttpObjectCallback callback = new HttpObjectCallback().withClientId("some_forward_client_id");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<String>(), false, true);

        // then
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(mockHttpForwardObjectCallbackActionHandler).handle(callback, request, mockResponseWriter, true);
    }

    @Test
    public void shouldProcessOverrideForwardedRequest() {
        // given
        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest().withHttpRequest(request("some_overridden_path"));
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(httpOverrideForwardedRequest);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<String>(), false, true);

        // then
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(mockHttpOverrideForwardedRequestActionHandler).handle(httpOverrideForwardedRequest, request);
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
        actionHandler.processAction(request, mockResponseWriter, mockChannelHandlerContext, new HashSet<String>(), false, true);

        // then
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(mockHttpErrorActionHandler).handle(error, mockChannelHandlerContext);
        verify(mockLogFormatter).info(EXPECTATION_RESPONSE, request, "returning error:{}for request:{}for action:{}", error, request, error);
    }

    @Test
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
        when(mockNettyHttpClient.sendRequest(request("request_one"), remoteAddress, ConfigurationProperties.socketConnectionTimeout())).thenReturn(responseFuture);

        // when
        actionHandler.processAction(request("request_one"), mockResponseWriter, mockChannelHandlerContext, new HashSet<String>(), true, true);

        // then
        verify(mockHttpStateHandler).log(new RequestResponseLogEntry(request, response("some_body")));
        verify(mockNettyHttpClient).sendRequest(request("request_one"), remoteAddress, ConfigurationProperties.socketConnectionTimeout());
        verify(mockLogFormatter).info(
            FORWARDED_REQUEST,
            request,
            "returning response:{}for request:{}as curl:{}",
            response("some_body"),
            request,
            new HttpRequestToCurlSerializer().toCurl(request, remoteAddress)
        );
    }
}
