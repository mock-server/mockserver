package org.mockserver.mock.action;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class ActionHandlerTest {

    @Mock
    private HttpCallbackActionHandler mockHttpCallbackActionHandler;

    @Mock
    private HttpForwardActionHandler mockHttpForwardActionHandler;

    @Mock
    private HttpForwardTemplateActionHandler mockHttpForwardTemplateActionHandler;

    @Mock
    private HttpResponseActionHandler mockHttpResponseActionHandler;

    @Mock
    private HttpResponseTemplateActionHandler mockHttpResponseTemplateActionHandler;

    @Mock
    private ResponseWriter mockResponseWriter;

    @Mock
    private LoggingFormatter logFormatter;

    private HttpStateHandler mockHttpStateHandler;
    private WebSocketClientRegistry mockWebSocketClientRegistry;
    private HttpRequest request;
    private HttpResponse response;
    private Expectation expectation;

    @InjectMocks
    private ActionHandler actionHandler;

    @Before
    public void setupMocks() {
        mockHttpStateHandler = mock(HttpStateHandler.class);
        mockWebSocketClientRegistry = mock(WebSocketClientRegistry.class);
        actionHandler = new ActionHandler(mockHttpStateHandler, mockWebSocketClientRegistry);
        initMocks(this);
        request = request("some_path");
        response = response("some_body");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(response);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        when(mockHttpCallbackActionHandler.handle(any(HttpClassCallback.class), any(HttpRequest.class))).thenReturn(response);
        when(mockHttpForwardActionHandler.handle(any(HttpForward.class), any(HttpRequest.class))).thenReturn(response);
        when(mockHttpForwardTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenReturn(response);
        when(mockHttpResponseActionHandler.handle(any(HttpResponse.class))).thenReturn(response);
        when(mockHttpResponseTemplateActionHandler.handle(any(HttpTemplate.class), any(HttpRequest.class))).thenReturn(response);
    }

    @Test
    public void shouldProcessCallbackAction() {
        // given
        HttpClassCallback callback = callback("some_class");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenCallback(callback);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null);

        // then
        verify(mockHttpCallbackActionHandler).handle(callback, request);
        verify(mockResponseWriter).writeResponse(request, response);
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(logFormatter).infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, callback);
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
        actionHandler.processAction(request, mockResponseWriter, null);

        // then
        verify(mockHttpForwardActionHandler).handle(forward, request);
        verify(mockResponseWriter).writeResponse(request, response);
        verify(mockHttpStateHandler, times(1)).log(new RequestResponseLogEntry(request, response));
        verify(logFormatter).infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, forward);
    }

    @Test
    public void shouldProcessForwardTemplateAction() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenForward(template);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null);

        // then
        verify(mockHttpForwardTemplateActionHandler).handle(template, request);
        verify(mockResponseWriter).writeResponse(request, response);
        verify(mockHttpStateHandler, times(1)).log(new RequestResponseLogEntry(request, response));
        verify(logFormatter).infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, template);
    }

    @Test
    public void shouldProcessResponseAction() {
        // given
        HttpResponse response = response("some_template");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(response);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null);

        // then
        verify(mockHttpResponseActionHandler).handle(response);
        verify(mockResponseWriter).writeResponse(request, this.response);
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(logFormatter).infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", this.response, request, response);
    }

    @Test
    public void shouldProcessResponseTemplateAction() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "some_template");
        expectation = new Expectation(request, Times.unlimited(), TimeToLive.unlimited()).thenRespond(template);
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(expectation);

        // when
        actionHandler.processAction(request, mockResponseWriter, null);

        // then
        verify(mockHttpResponseTemplateActionHandler).handle(template, request);
        verify(mockResponseWriter).writeResponse(request, response);
        verify(mockHttpStateHandler, times(1)).log(new ExpectationMatchLogEntry(request, expectation));
        verify(logFormatter).infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, template);
    }
}