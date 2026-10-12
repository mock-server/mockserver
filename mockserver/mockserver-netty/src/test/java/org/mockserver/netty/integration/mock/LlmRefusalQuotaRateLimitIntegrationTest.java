package org.mockserver.netty.integration.mock;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.llm.LlmQuotaRegistry;
import org.mockserver.llm.LlmRefusalPresets;
import org.mockserver.model.LlmChaosProfile;
import org.mockserver.model.Provider;
import org.mockserver.netty.MockServer;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;

/**
 * End-to-end coverage for an {@code httpLlmResponse} that combines a provider-style
 * <em>refusal</em> preset with a stateful request-count <em>quota</em> and the
 * provider-specific <em>rate-limit headers</em> that ride alongside it. Everything the
 * refusal / rate-limit / quota builders produce is otherwise only asserted at the
 * body-builder / handler-unit level; this test drives the whole serve path through a
 * running {@link MockServer} and asserts what a real client receives on the wire —
 * the refusal envelope, the {@code anthropic-ratelimit-*} headers, and the flip to a
 * {@code 429} {@code rate_limit_error} quota-exceeded envelope on the request that
 * exceeds the quota.
 *
 * <p>A raw socket is used so the on-the-wire status line, response headers, and JSON
 * body can all be asserted directly from a single served response string, rather than
 * reconstructed from the builders in isolation.
 *
 * <p>The stateful {@link LlmQuotaRegistry} is a process-wide singleton, so it is
 * {@link LlmQuotaRegistry#reset() reset} before the test and the expectation uses a
 * uniquely-named quota to stay isolated from any concurrent LLM test.
 */
public class LlmRefusalQuotaRateLimitIntegrationTest {

    private MockServer server;
    private int port;
    private MockServerClient mockServerClient;

    @Before
    public void startServer() {
        LlmQuotaRegistry.getInstance().reset();
        server = new MockServer();
        port = server.getLocalPort();
        mockServerClient = new MockServerClient("localhost", port);
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    /**
     * POST an Anthropic-shaped chat body over a raw socket and return the full raw HTTP
     * response (status line + headers + body) as a single string, so both headers and
     * body can be asserted together.
     */
    private String postReadRawResponse() throws Exception {
        String body = "{\"model\":\"claude-3-5-sonnet\",\"max_tokens\":16,"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"do something disallowed\"}]}";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            OutputStream output = socket.getOutputStream();
            output.write(("POST /v1/messages HTTP/1.1\r\n" +
                "Host: localhost:" + port + "\r\n" +
                "Connection: close\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + bodyBytes.length + "\r\n" +
                "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bodyBytes);
            output.flush();
            return IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void shouldServeRefusalWithRateLimitHeadersThenFlipToQuotaExceeded() throws Exception {
        // given - an httpLlmResponse serving an Anthropic refusal, guarded by a request-count
        // quota of 2 requests / 60s (so the 3rd request must flip to a 429 quota breach)
        mockServerClient
            .when(request().withMethod("POST").withPath("/v1/messages"))
            .respondWithLlm(llmResponse()
                .withProvider(Provider.ANTHROPIC)
                .withModel("claude-3-5-sonnet")
                .withCompletion(LlmRefusalPresets.anthropicRefusal("I can't help with that."))
                .withChaos(LlmChaosProfile.llmChaosProfile()
                    .withQuotaName("refusal-quota-" + System.nanoTime())
                    .withQuotaLimit(2)
                    .withQuotaWindowMillis(60_000L)));

        // when - the first request is within the quota
        String first = postReadRawResponse();
        // then - a 200 refusal envelope is served, carrying the Anthropic rate-limit headers
        // (limit + reset) but no Retry-After (not limited)
        assertThat(first, containsString("200"));
        assertThat("refusal stop_reason must reach the client", first, containsString("\"stop_reason\":\"refusal\""));
        assertThat(first, containsString("\"stop_details\":{\"type\":\"refusal\",\"category\":null,\"explanation\":null}"));
        assertThat(first, containsString("anthropic-ratelimit-requests-limit: 2"));
        assertThat(first, containsString("anthropic-ratelimit-requests-reset:"));
        assertThat("a within-quota response must not carry Retry-After", first, not(containsString("Retry-After:")));

        // when - the second request is still within the quota
        String second = postReadRawResponse();
        // then - still a 200 refusal envelope with the rate-limit headers
        assertThat(second, containsString("200"));
        assertThat(second, containsString("\"stop_reason\":\"refusal\""));
        assertThat(second, containsString("anthropic-ratelimit-requests-limit: 2"));

        // when - the third request exceeds the quota
        String third = postReadRawResponse();
        // then - the response flips to a 429 quota-exceeded envelope: the provider-correct
        // Anthropic rate_limit_error body, the exhausted rate-limit headers, and Retry-After
        assertThat("Nth over-quota request must return 429", third, containsString("429"));
        assertThat("must be the Anthropic rate_limit_error envelope", third, containsString("rate_limit_error"));
        assertThat(third, containsString("anthropic-ratelimit-requests-limit: 2"));
        assertThat(third, containsString("anthropic-ratelimit-requests-remaining: 0"));
        assertThat(third, containsString("anthropic-ratelimit-requests-reset:"));
        assertThat(third, containsString("Retry-After: 60"));
        // and - the over-quota response is an error, not the refusal completion
        assertThat(third, not(containsString("\"stop_reason\":\"refusal\"")));
    }
}
