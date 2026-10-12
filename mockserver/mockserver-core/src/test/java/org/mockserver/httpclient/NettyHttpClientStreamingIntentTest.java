package org.mockserver.httpclient;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;

/**
 * Unit tests for {@link NettyHttpClient#requestExpectsStreamingResponse}, the signal that makes
 * MockServer relay a response as a stream even when the upstream omits
 * {@code Content-Type: text/event-stream} (e.g. the OpenAI Codex backend used by opencode).
 */
public class NettyHttpClientStreamingIntentTest {

    @Test
    public void detectsStreamTrueInJsonBody() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withHeader("content-type", "application/json").withBody("{\"model\":\"x\",\"stream\":true}")), is(true));
    }

    @Test
    public void detectsStreamTrueWithWhitespace() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withHeader("content-type", "application/json").withBody("{\"stream\" : true}")), is(true));
    }

    @Test
    public void detectsStreamTrueAcrossNewlines() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withHeader("content-type", "application/json").withBody("{\n  \"stream\":\n  true\n}")), is(true));
    }

    @Test
    public void doesNotDetectStreamFalse() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withHeader("content-type", "application/json").withBody("{\"stream\":false}")), is(false));
    }

    @Test
    public void doesNotDetectMissingStreamField() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withHeader("content-type", "application/json").withBody("{\"model\":\"x\"}")), is(false));
    }

    @Test
    public void detectsAcceptEventStreamHeaderRegardlessOfBody() {
        // The Accept signal applies even with no body / non-JSON content type.
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withHeader("accept", "text/event-stream")), is(true));
    }

    @Test
    public void detectsAcceptEventStreamAmongMultipleValues() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withHeader("accept", "application/json, text/event-stream;q=0.9")), is(true));
    }

    @Test
    public void doesNotScanNonJsonBodyForStreamFlag() {
        // The body is only scanned for the stream flag when the request declares a JSON content type,
        // so a stray "stream":true substring in a non-JSON payload is not misread.
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withHeader("content-type", "text/plain").withBody("\"stream\":true")), is(false));
    }

    @Test
    public void doesNotDetectPlainNonStreamingRequest() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withHeader("content-type", "application/json").withBody("{\"q\":\"hi\"}")), is(false));
    }

    @Test
    public void handlesNullRequest() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(null), is(false));
    }

    @Test
    public void handlesRequestWithNoBodyOrHeaders() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(request()), is(false));
    }

    @Test
    public void detectsGeminisStreamingMethodWhichSendsItsJsonArrayAsApplicationJson() {
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withPath("/v1beta/models/gemini-2.5-flash:streamGenerateContent")
                .withHeader("content-type", "application/json").withBody("{\"contents\":[]}")), is(true));
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withPath("/v1/projects/p/locations/us-central1/publishers/google/models/gemini-2.5-pro:streamGenerateContent")), is(true));
        assertThat(NettyHttpClient.requestExpectsStreamingResponse(
            request().withPath("/v1beta/models/gemini-2.5-flash:generateContent")
                .withHeader("content-type", "application/json").withBody("{\"contents\":[]}")), is(false));
    }
}
