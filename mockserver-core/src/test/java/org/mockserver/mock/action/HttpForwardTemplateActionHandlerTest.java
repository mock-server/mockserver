package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;

import javax.script.ScriptEngineManager;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpForwardTemplateActionHandlerTest {

    private NettyHttpClient mockHttpClient;
    private HttpForwardTemplateActionHandler httpForwardTemplateActionHandler;

    @Before
    public void setupMocks() {
        mockHttpClient = mock(NettyHttpClient.class);
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler(mockLogFormatter, mockHttpClient);
        initMocks(this);
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateFirstExample() throws Exception {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "return { 'path': \"somePath\", 'body': JSON.stringify({name: 'value'}) };");
        HttpRequest httpRequest = request("somePath").withBody("{\"name\":\"value\"}");
        SettableFuture<HttpResponse> httpResponse = SettableFuture.create();
        when(mockHttpClient.sendRequest(httpRequest, null)).thenReturn(httpResponse);

        // when
        SettableFuture<HttpResponse> actualHttpResponse = httpForwardTemplateActionHandler.handle(template, request()
                .withPath("/somePath")
                .withMethod("POST")
                .withBody("some_body")
        );

        // then
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            verify(mockHttpClient).sendRequest(httpRequest, null);
            assertThat(actualHttpResponse, is(sameInstance(httpResponse)));
        } else {
            assertThat(actualHttpResponse.get(), is(notFoundResponse()));
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateFirstExample() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "{" + NEW_LINE +
                "    'path': \"$!request.path\"," + NEW_LINE +
                "    'method': \"$!request.method\"," + NEW_LINE +
                "    'body': \"$!request.body\"" + NEW_LINE +
                "}");
        HttpRequest httpRequest = request("/somePath").withMethod("POST").withBody("some_body");
        SettableFuture<HttpResponse> httpResponse = SettableFuture.create();
        when(mockHttpClient.sendRequest(httpRequest, null)).thenReturn(httpResponse);

        // when
        SettableFuture<HttpResponse> actualHttpResponse = httpForwardTemplateActionHandler.handle(template, httpRequest);

        // then
        verify(mockHttpClient).sendRequest(httpRequest, null);
        assertThat(actualHttpResponse, is(sameInstance(httpResponse)));
    }

}
