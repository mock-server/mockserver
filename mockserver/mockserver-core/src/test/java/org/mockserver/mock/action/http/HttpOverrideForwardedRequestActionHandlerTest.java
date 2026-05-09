package org.mockserver.mock.action.http;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.model.HttpTemplate.TemplateType;

import javax.script.ScriptEngineManager;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.templates.engine.javascript.JavaScriptTemplateEngineTest.nashornAvailable;

public class HttpOverrideForwardedRequestActionHandlerTest {

    private NettyHttpClient mockHttpClient;
    private HttpOverrideForwardedRequestActionHandler handler;

    @Before
    public void setupMocks() {
        mockHttpClient = mock(NettyHttpClient.class);
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        handler = new HttpOverrideForwardedRequestActionHandler(mockLogFormatter, new Configuration(), mockHttpClient);
        openMocks(this);
    }

    @Test
    public void shouldForwardRequestWithNoOverrides() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpForwardActionResult result = handler.handle(null, request("/somePath"));

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(200));
        assertThat(actualResponse.getBodyAsString(), is("upstream"));
    }

    @Test
    public void shouldForwardRequestWithRequestOverride() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        handler.handle(
            forwardOverriddenRequest(request("/overriddenPath")),
            request("/originalPath")
        );

        verify(mockHttpClient).sendRequest(
            argThat(req -> "/overriddenPath".equals(req.getPath().getValue())),
            any()
        );
    }

    @Test
    public void shouldApplyResponseOverrideToUpstreamResponse() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpForwardActionResult result = handler.handle(
            forwardOverriddenRequest()
                .withResponseOverride(response().withStatusCode(201).withBody("overridden")),
            request("/somePath")
        );

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(201));
        assertThat(actualResponse.getBodyAsString(), is("overridden"));
    }

    @Test
    public void shouldApplyResponseTemplateWithVelocity() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpTemplate velocityTemplate = template(TemplateType.VELOCITY,
            "{" + NEW_LINE +
                "    'statusCode': 202," + NEW_LINE +
                "    'body': \"request=$!request.path response=$!response.body\"" + NEW_LINE +
                "}"
        );

        HttpForwardActionResult result = handler.handle(
            forwardOverriddenRequest().withResponseTemplate(velocityTemplate),
            request("/somePath")
        );

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(202));
        assertThat(actualResponse.getBodyAsString(), is("request=/somePath response=upstream_body"));
    }

    @Test
    public void shouldApplyResponseTemplateWithJavaScript() throws Exception {
        nashornAvailable();
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpTemplate jsTemplate = template(TemplateType.JAVASCRIPT,
            "return {" + NEW_LINE +
                "    'statusCode': 203," + NEW_LINE +
                "    'body': 'path=' + request.path + ' status=' + response.statusCode" + NEW_LINE +
                "};"
        );

        HttpForwardActionResult result = handler.handle(
            forwardOverriddenRequest().withResponseTemplate(jsTemplate),
            request("/testPath")
        );

        HttpResponse actualResponse = result.getHttpResponse().get();
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            assertThat(actualResponse.getStatusCode(), is(203));
            assertThat(actualResponse.getBodyAsString(), is("path=/testPath status=200"));
        }
    }

    @Test
    public void shouldApplyResponseTemplateWithMustache() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpTemplate mustacheTemplate = template(TemplateType.MUSTACHE,
            "{" + NEW_LINE +
                "    'statusCode': 204," + NEW_LINE +
                "    'body': \"path={{ request.path }} status={{ response.statusCode }}\"" + NEW_LINE +
                "}"
        );

        HttpForwardActionResult result = handler.handle(
            forwardOverriddenRequest().withResponseTemplate(mustacheTemplate),
            request("/mustachePath")
        );

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(204));
        assertThat(actualResponse.getBodyAsString(), is("path=/mustachePath status=200"));
    }

    @Test
    public void shouldApplyResponseTemplateAfterResponseOverride() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpTemplate velocityTemplate = template(TemplateType.VELOCITY,
            "{" + NEW_LINE +
                "    'statusCode': 210," + NEW_LINE +
                "    'body': \"saw=$!response.body\"" + NEW_LINE +
                "}"
        );

        HttpForwardActionResult result = handler.handle(
            forwardOverriddenRequest()
                .withResponseOverride(response().withBody("overridden_body"))
                .withResponseTemplate(velocityTemplate),
            request("/somePath")
        );

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(210));
        assertThat(actualResponse.getBodyAsString(), is("saw=overridden_body"));
    }

    @Test
    public void shouldNotApplyResponseTemplateWhenUpstreamResponseIsNull() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(null);
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpTemplate velocityTemplate = template(TemplateType.VELOCITY,
            "{ 'statusCode': 999 }"
        );

        HttpForwardActionResult result = handler.handle(
            forwardOverriddenRequest().withResponseTemplate(velocityTemplate),
            request("/somePath")
        );

        assertThat(result.getHttpResponse().get(), is((HttpResponse) null));
    }

    @Test
    public void shouldForwardWithoutResponseTemplateWhenTemplateIsNull() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpForwardActionResult result = handler.handle(
            forwardOverriddenRequest(),
            request("/somePath")
        );

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(200));
        assertThat(actualResponse.getBodyAsString(), is("upstream"));
    }

    @Test
    public void shouldApplyResponseTemplateWithResponseOverride() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream").withHeaders(
            new Header("X-Original", "yes")
        ));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpTemplate velocityTemplate = template(TemplateType.VELOCITY,
            "{" + NEW_LINE +
                "    'statusCode': 230," + NEW_LINE +
                "    'body': \"body=$!response.body\"" + NEW_LINE +
                "}"
        );

        HttpForwardActionResult result = handler.handle(
            forwardOverriddenRequest()
                .withResponseOverride(response().withBody("overridden"))
                .withResponseTemplate(velocityTemplate),
            request("/somePath")
        );

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(230));
        assertThat(actualResponse.getBodyAsString(), is("body=overridden"));
    }
}
