package org.mockserver.mock.action.http;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.model.HttpTemplate.TemplateType;

import javax.script.ScriptEngineManager;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.badGatewayResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpResponseModifier.responseModifier;
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
            assertThat(actualHttpResponse.get(), is(badGatewayResponse()));
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

    @Test
    public void shouldApplyResponseOverrideToForwardedResponse() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpTemplate tmpl = template(TemplateType.VELOCITY, "{" + NEW_LINE +
            "    'path': \"$!request.path\"" + NEW_LINE +
            "}")
            .withResponseOverride(response().withStatusCode(201).withBody("overridden"));

        HttpForwardActionResult result = httpForwardTemplateActionHandler
            .handle(tmpl, request("/somePath").withMethod("GET"));

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(201));
        assertThat(actualResponse.getBodyAsString(), is("overridden"));
    }

    @Test
    public void shouldApplyResponseModifierToForwardedResponse() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream")
            .withHeader("X-Existing", "value"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpTemplate tmpl = template(TemplateType.VELOCITY, "{" + NEW_LINE +
            "    'path': \"$!request.path\"" + NEW_LINE +
            "}")
            .withResponseModifier(responseModifier()
                .withHeaders(
                    Collections.singletonList(new Header("X-Added", "new")),
                    Collections.emptyList(),
                    Collections.emptyList()
                ));

        HttpForwardActionResult result = httpForwardTemplateActionHandler
            .handle(tmpl, request("/somePath").withMethod("GET"));

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(200));
        assertThat(actualResponse.getHeader("X-Added"), contains("new"));
        assertThat(actualResponse.getHeader("X-Existing"), contains("value"));
    }

    @Test
    public void shouldApplyBothResponseOverrideAndModifier() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpTemplate tmpl = template(TemplateType.VELOCITY, "{" + NEW_LINE +
            "    'path': \"$!request.path\"" + NEW_LINE +
            "}")
            .withResponseOverride(response().withStatusCode(201).withBody("overridden"))
            .withResponseModifier(responseModifier()
                .withHeaders(
                    Collections.singletonList(new Header("X-Added", "new")),
                    Collections.emptyList(),
                    Collections.emptyList()
                ));

        HttpForwardActionResult result = httpForwardTemplateActionHandler
            .handle(tmpl, request("/somePath").withMethod("GET"));

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(201));
        assertThat(actualResponse.getBodyAsString(), is("overridden"));
        assertThat(actualResponse.getHeader("X-Added"), contains("new"));
    }

}
