package org.mockserver.mock.action;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.model.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.internal.verification.VerificationModeFactory.times;
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
    private HttpResponseActionHandler mockHttpResponseActionHandler;

    @Mock
    private HttpResponseTemplateActionHandler mockHttpResponseTemplateActionHandler;

    private RequestLogFilter requestLogFilter;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;

    @InjectMocks
    private ActionHandler actionHandler;

    @Before
    public void setupMocks() {
        requestLogFilter = mock(RequestLogFilter.class);
        actionHandler = new ActionHandler(requestLogFilter);
        initMocks(this);
        httpRequest = request("some_path");
        httpResponse = response("some_body");
        when(requestLogFilter.onRequest(httpRequest)).thenReturn(httpRequest);
        when(requestLogFilter.onResponse(httpRequest, httpResponse)).thenReturn(httpResponse);
    }

    @Test
    public void shouldProcessCallbackAction() {
        // given
        HttpClassCallback httpClassCallback = callback();
        when(mockHttpCallbackActionHandler.handle(httpClassCallback, httpRequest)).thenReturn(httpResponse);

        // when
        HttpResponse actualHttpResponse = actionHandler.processAction(httpClassCallback, httpRequest);

        // then
        assertThat(actualHttpResponse, is(httpResponse));
        verify(requestLogFilter, times(1)).onRequest(httpRequest);
    }

    @Test
    public void shouldProcessForwardAction() {
        // given
        HttpForward httpForward = forward();
        when(mockHttpForwardActionHandler.handle(httpForward, httpRequest)).thenReturn(httpResponse);

        // when
        HttpResponse actualHttpResponse = actionHandler.processAction(httpForward, httpRequest);

        // then
        assertThat(actualHttpResponse, is(httpResponse));
        verify(requestLogFilter, times(1)).onRequest(httpRequest);
    }

    @Test
    public void shouldProcessResponseAction() {
        // given
        when(mockHttpResponseActionHandler.handle(httpResponse)).thenReturn(httpResponse);

        // when
        HttpResponse actualHttpResponse = actionHandler.processAction(httpResponse, httpRequest);

        // then
        assertThat(actualHttpResponse, is(httpResponse));
        verify(requestLogFilter, times(1)).onRequest(httpRequest);
    }

    @Test
    public void shouldProcessResponseTemplateAction() {
        // given
        HttpTemplate httpTemplate = template();
        when(mockHttpResponseTemplateActionHandler.handle(httpTemplate, httpRequest)).thenReturn(httpResponse);

        // when
        HttpResponse actualHttpResponse = actionHandler.processAction(httpTemplate, httpRequest);

        // then
        assertThat(actualHttpResponse, is(httpResponse));
        verify(requestLogFilter, times(1)).onRequest(httpRequest);
    }
}