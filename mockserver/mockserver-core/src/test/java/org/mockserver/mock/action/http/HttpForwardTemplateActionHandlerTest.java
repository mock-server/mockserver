package org.mockserver.mock.action.http;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;

import javax.script.ScriptEngineManager;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.templates.engine.javascript.JavaScriptTemplateEngineTest.nashornAvailable;

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
        httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler(mockLogFormatter, new Configuration(), mockHttpClient);
        openMocks(this);
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateFirstExample() throws Exception {
        // given
        nashornAvailable();
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "return { 'path': \"somePath\", 'body': JSON.stringify({name: 'value'}) };");
        HttpRequest httpRequest = request("somePath").withBody("{\"name\":\"value\"}");
        CompletableFuture<HttpResponse> httpResponse = new CompletableFuture<>();
        when(mockHttpClient.sendRequest(httpRequest, null)).thenReturn(httpResponse);

        // when
        CompletableFuture<HttpResponse> actualHttpResponse = httpForwardTemplateActionHandler
            .handle(template, request()
                .withPath("/somePath")
                .withMethod("POST")
                .withBody("some_body")
            )
            .getHttpResponse();

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
        CompletableFuture<HttpResponse> httpResponse = new CompletableFuture<>();
        when(mockHttpClient.sendRequest(httpRequest, null)).thenReturn(httpResponse);

        // when
        CompletableFuture<HttpResponse> actualHttpResponse = httpForwardTemplateActionHandler
            .handle(template, httpRequest)
            .getHttpResponse();

        // then
        verify(mockHttpClient).sendRequest(httpRequest, null);
        assertThat(actualHttpResponse, is(sameInstance(httpResponse)));
    }

}
