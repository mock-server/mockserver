package org.mockserver.mock.action.http;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.model.HttpTemplate.TemplateType;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.templates.engine.javascript.JavaScriptTemplateEngineTest.graalJsAvailable;

public class HttpOverrideForwardedRequestActionHandlerTest {

    private NettyHttpClient mockHttpClient;
    private HttpOverrideForwardedRequestActionHandler handler;

    @Before
    public void setupMocks() {
        mockHttpClient = mock(NettyHttpClient.class);
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        // javascriptTemplateExecutionTimeout(0L) disables the JS template watchdog (see PolyglotRunner):
        // these tests exercise normal JS template execution, not the timeout feature, so they must not
        // depend on wall-clock time. Under parallel CI load GraalJS runs interpreter-only and a first
        // execute can exceed the production default, making this flaky. Production default is unchanged.
        handler = new HttpOverrideForwardedRequestActionHandler(mockLogFormatter, new Configuration().javascriptTemplateExecutionTimeout(0L), mockHttpClient);
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
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean())).thenReturn(responseFuture);

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
    public void shouldNotDisableStreamingForHeaderOnlyResponseOverride() throws Exception {
        // A header-only response override (no body, no schema) can be applied to a streaming
        // response HEAD while the body is relayed untouched, so streaming must NOT be disabled:
        // the handler uses the non-disableStreaming httpClient overload (2-arg sendRequest).
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpForwardActionResult result = handler.handle(
            forwardOverriddenRequest()
                .withResponseOverride(response().withHeader("X-Trace", "abc")),
            request("/somePath")
        );

        // the streaming (non-disableStreaming) overload was used
        verify(mockHttpClient).sendRequest(any(HttpRequest.class), any());
        verify(mockHttpClient, never()).sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean());
        // and the header override is still applied to the (aggregated, in this unit test) response
        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getFirstHeader("X-Trace"), is("abc"));
        assertThat(actualResponse.getBodyAsString(), is("upstream"));
    }

    @Test
    public void shouldNotDisableStreamingForHeaderOnlyResponseModifier() throws Exception {
        // A response MODIFIER that only edits headers (no JSON body patch) is head-only, so streaming
        // is preserved (2-arg sendRequest).
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        handler.handle(
            forwardOverriddenRequest()
                .withResponseModifier(HttpResponseModifier.responseModifier()
                    .withHeaders(java.util.Collections.singletonList(new Header("X-Add", "1")), java.util.Collections.emptyList(), java.util.Collections.emptyList())),
            request("/somePath")
        );

        verify(mockHttpClient).sendRequest(any(HttpRequest.class), any());
        verify(mockHttpClient, never()).sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean());
    }

    @Test
    public void shouldDisableStreamingWhenResponseModifierPatchesBody() throws Exception {
        // A response modifier that patches the JSON body needs the full buffered body, so streaming
        // must be disabled (4-arg sendRequest with disableStreaming=true).
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("{\"a\":1}"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean())).thenReturn(responseFuture);

        handler.handle(
            forwardOverriddenRequest()
                .withResponseModifier(HttpResponseModifier.responseModifier()
                    .withJsonMergePatch("{\"a\":2}")),
            request("/somePath")
        );

        verify(mockHttpClient).sendRequest(any(HttpRequest.class), any(), anyLong(), eq(true));
        verify(mockHttpClient, never()).sendRequest(any(HttpRequest.class), any());
    }

    @Test
    public void shouldConservativelyDisableStreamingForConditionGatedBodyPatch() throws Exception {
        // A body patch guarded by a condition may not run at runtime, but streaming is decided before
        // the response arrives, so a body patch (conditional or not) conservatively disables streaming.
        assertThat(HttpOverrideForwardedRequestActionHandler.isHeaderOnlyResponseModification(
            forwardOverriddenRequest()
                .withResponseModifier(HttpResponseModifier.responseModifier()
                    .withCondition(new HttpResponseModifierCondition().withStatusCodeRange("2xx"))
                    .withJsonMergePatch("{\"a\":2}"))
        ), is(false));
    }

    @Test
    public void shouldTreatChainOfHeaderOnlyModifiersAsHeaderOnly() throws Exception {
        // A chain of header-only child modifiers is header-only. The wrapping modifier's OWN
        // jsonPatch is ignored by applyTo() when a chain is present, so it does not count against
        // header-only classification.
        assertThat(HttpOverrideForwardedRequestActionHandler.isHeaderOnlyResponseModification(
            forwardOverriddenRequest()
                .withResponseModifier(HttpResponseModifier.responseModifier()
                    .withJsonMergePatch("{\"ignored\":true}")
                    .withModifiers(java.util.Collections.singletonList(
                        HttpResponseModifier.responseModifier()
                            .withHeaders(java.util.Collections.singletonList(new Header("X-Add", "1")), java.util.Collections.emptyList(), java.util.Collections.emptyList()))))
        ), is(true));
    }

    @Test
    public void shouldDisableStreamingForChainContainingBodyPatchModifier() throws Exception {
        // A chain that contains a child which patches the body is body-affecting.
        assertThat(HttpOverrideForwardedRequestActionHandler.isHeaderOnlyResponseModification(
            forwardOverriddenRequest()
                .withResponseModifier(HttpResponseModifier.responseModifier()
                    .withModifiers(java.util.Collections.singletonList(
                        HttpResponseModifier.responseModifier().withJsonMergePatch("{\"a\":2}"))))
        ), is(false));
    }

    @Test
    public void shouldApplyResponseTemplateWithVelocity() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean())).thenReturn(responseFuture);

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
        graalJsAvailable();
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean())).thenReturn(responseFuture);

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
        if (JavaScriptTemplateEngine.isPolyglotAvailable()) {
            assertThat(actualResponse.getStatusCode(), is(203));
            assertThat(actualResponse.getBodyAsString(), is("path=/testPath status=200"));
        }
    }

    @Test
    public void shouldApplyResponseTemplateWithMustache() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean())).thenReturn(responseFuture);

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
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean())).thenReturn(responseFuture);

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
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean())).thenReturn(responseFuture);

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
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean())).thenReturn(responseFuture);

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

    @Test
    public void shouldKeepUpstreamResponseWhenResponseTemplateOutputIsInvalidByDefault() throws Exception {
        // given - a response template whose output is not a valid response
        HttpForwardActionResult result = forwardWithResponseTemplate(handler, "{ 'statusCode': 'two hundred' }");

        // when
        HttpResponse actualResponse = result.getHttpResponse().get();

        // then
        assertThat(actualResponse.getStatusCode(), is(200));
        assertThat(actualResponse.getBodyAsString(), is("upstream"));
    }

    @Test
    public void shouldNotMarkThrowingResponseTemplateAsTemplateFailureByDefault() {
        // given - a response template whose parse fails
        HttpForwardActionResult result = forwardWithResponseTemplate(handler, "#foreach(");

        // when
        ExecutionException failure = assertThrows(ExecutionException.class, () -> result.getHttpResponse().get());

        // then - the engine's failure, as before
        assertThat(TemplateFailureException.in(failure), is(nullValue()));
        assertThat(failure.getCause().getMessage(), containsString("transforming template"));
    }

    @Test
    public void shouldReportThrowingResponseTemplateAsTemplateFailureWhenConfigured() {
        // given
        HttpForwardActionResult result = forwardWithResponseTemplate(serverErrorHandler(), "#foreach(");

        // when
        ExecutionException failure = assertThrows(ExecutionException.class, () -> result.getHttpResponse().get());

        // then
        TemplateFailureException templateFailure = TemplateFailureException.in(failure);
        assertThat(templateFailure.getDetail(), startsWith("VELOCITY forward response template failed: "));
        assertThat(templateFailure.loggedFailure().getMessage(), containsString("transforming template"));
    }

    @Test
    public void shouldReportInvalidResponseTemplateOutputAsTemplateFailureWhenConfigured() {
        // given
        HttpForwardActionResult result = forwardWithResponseTemplate(serverErrorHandler(), "{ 'statusCode': 'two hundred' }");

        // when
        ExecutionException failure = assertThrows(ExecutionException.class, () -> result.getHttpResponse().get());

        // then
        TemplateFailureException templateFailure = TemplateFailureException.in(failure);
        assertThat(templateFailure.getDetail(), startsWith("VELOCITY forward response template rendered output that is not a valid response: "));
        assertThat(templateFailure.getDetail(), containsString("statusCode"));
    }

    private HttpOverrideForwardedRequestActionHandler serverErrorHandler() {
        return new HttpOverrideForwardedRequestActionHandler(mock(MockServerLogger.class), new Configuration().templateFailureAsServerError(true), mockHttpClient);
    }

    private HttpForwardActionResult forwardWithResponseTemplate(HttpOverrideForwardedRequestActionHandler forwardHandler, String velocityTemplate) {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(), anyLong(), anyBoolean())).thenReturn(responseFuture);
        return forwardHandler.handle(
            forwardOverriddenRequest().withResponseTemplate(template(TemplateType.VELOCITY, velocityTemplate)),
            request("/somePath")
        );
    }
}
