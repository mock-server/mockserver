package org.mockserver.netty.telemetry;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.After;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Completion;
import org.mockserver.model.Provider;
import org.mockserver.model.Usage;
import org.mockserver.netty.MockServer;
import org.mockserver.telemetry.GenAiSpanExporter;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;

/**
 * End-to-end behavioural guard for the SERVE-path GenAI span emission that
 * {@code HttpLlmResponseActionHandler} performs when it serves a locally-mocked
 * {@code httpLlmResponse} completion (the serve-side counterpart to
 * {@code HttpActionHandler.emitForwardGenAiSpan}, covered by
 * {@link ForwardPathGenAiSpanEmissionTest}). Where the forward test proxies to an
 * upstream and parses the upstream's response, this test asserts the span MockServer
 * emits for a completion it invents itself and returns from an expectation — a
 * distinct production code path (the {@code GenAiSpans.recordCompletion(...)} call inside
 * {@code HttpLlmResponseActionHandler.handle}, reached only for a non-streaming served
 * completion), not the forward path.
 *
 * <p>The exporter is installed through the supported {@link GenAiSpanExporter#startWithProcessor}
 * seam, which wires the process-wide tracer that production
 * {@code GenAiSpans.recordCompletion(provider, model, completion)} emits into — so the span
 * asserted here can only exist if the running server executed the serve-path emission after
 * encoding the served completion. It is read back out of the in-process
 * {@link InMemorySpanExporter}, not reconstructed by the test.
 *
 * <p>Unlike the forward path, provider detection is not involved: the served
 * {@code httpLlmResponse} carries its own {@link Provider}, so no {@code mockserver.llmProvider}
 * fallback or host gating is needed. Explicit non-zero {@link Usage} is supplied on the
 * completion so it survives {@code withInferredUsageIfEnabled} unchanged and the span carries
 * the exact token counts asserted here.
 *
 * <p>Mutates process-wide static state (the {@code GenAiSpans}/{@code RequestSpans} tracer via
 * the exporter); it is restored in {@link #tearDown()}. Named {@code *Test} so it runs in the
 * fast surefire phase (mockserver-netty surefire runs classes sequentially in a single fork, so
 * the global-state mutation is safe).
 */
public class ServePathGenAiSpanEmissionTest {

    private GenAiSpanExporter exporter;
    private MockServer server;

    @After
    public void tearDown() {
        if (exporter != null) {
            // restores both the GenAiSpans and RequestSpans process-wide tracers to null
            exporter.stop();
            exporter = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    public void shouldEmitGenAiSpanFromProductionServePathForServedCompletion() throws Exception {
        // given - an in-memory exporter wired into the process-wide tracer that the production
        // serve path emits GenAI spans through (SimpleSpanProcessor exports synchronously on end)
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        exporter = GenAiSpanExporter.startWithProcessor(SimpleSpanProcessor.create(spanExporter));

        // and - a MockServer stubbed to SERVE a locally-mocked OpenAI chat completion
        server = new MockServer();
        int port = server.getLocalPort();
        new MockServerClient("localhost", port)
            .when(request().withMethod("POST").withPath("/v1/chat/completions"))
            .respondWithLlm(llmResponse()
                .withProvider(Provider.OPENAI)
                .withModel("gpt-4o")
                .withCompletion(Completion.completion()
                    .withText("Hello! How can I help?")
                    .withStopReason("stop")
                    // explicit non-zero usage: survives usage inference unchanged
                    .withUsage(Usage.usage().withInputTokens(12).withOutputTokens(8))));

        // when - a client POSTs a chat-completion request that the server answers locally
        int status = postJson("http://localhost:" + port + "/v1/chat/completions",
            "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}");
        assertThat("served LLM request must succeed", status, is(200));

        // then - the production serve path emitted exactly one GenAI span, visible via the exporter
        SpanData span = awaitSingleGenAiSpan(spanExporter);
        assertThat(span.getName(), is("chat gpt-4o"));
        assertThat(span.getAttributes().get(stringKey("gen_ai.operation.name")), is("chat"));
        assertThat(span.getAttributes().get(stringKey("gen_ai.system")), is("openai"));
        assertThat(span.getAttributes().get(stringKey("gen_ai.request.model")), is("gpt-4o"));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(12L));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.output_tokens")), is(8L));
    }

    /**
     * The OpenTelemetry GenAI semantic conventions say a well-known {@code gen_ai.operation.name}
     * MUST be used where one applies: {@code generate_content} for Gemini Generate Content and
     * {@code text_completion} for a legacy (OpenAI Completions API) text completion.
     * https://github.com/open-telemetry/semantic-conventions-genai/blob/main/docs/gen-ai/gen-ai-spans.md
     */
    @Test
    public void shouldNameEachServedCompletionByTheSemanticConventionOperationOfItsEndpoint() throws Exception {
        assertServedOperation(Provider.GEMINI, "gemini-2.5-flash", "/v1beta/models/gemini-2.5-flash:generateContent",
            "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"hello\"}]}]}", false, "generate_content");
        assertServedOperation(Provider.GEMINI, "gemini-2.5-flash", "/v1beta/models/gemini-2.5-flash:streamGenerateContent",
            "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"hello\"}]}]}", true, "generate_content");
        assertServedOperation(Provider.OPENAI, "gpt-3.5-turbo-instruct", "/v1/completions",
            "{\"model\":\"gpt-3.5-turbo-instruct\",\"prompt\":\"Say this is a test\"}", false, "text_completion");
        assertServedOperation(Provider.MISTRAL, "codestral-2508", "/v1/fim/completions",
            "{\"model\":\"codestral-2508\",\"prompt\":\"def f(\",\"suffix\":\"return a + b\"}", false, "text_completion");
        assertServedOperation(Provider.OLLAMA, "llama3.2", "/api/generate",
            "{\"model\":\"llama3.2\",\"prompt\":\"Why is the sky blue?\",\"stream\":false}", false, "text_completion");
        assertServedOperation(Provider.OLLAMA, "llama3.2", "/api/generate",
            "{\"model\":\"llama3.2\",\"prompt\":\"Why is the sky blue?\"}", true, "text_completion");
        assertServedOperation(Provider.OLLAMA, "llama3.2", "/api/chat",
            "{\"model\":\"llama3.2\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}", true, "chat");
    }

    private void assertServedOperation(Provider provider, String model, String path, String requestBody, boolean streaming,
                                       String expectedOperation) throws Exception {
        if (server == null) {
            server = new MockServer();
        }
        if (exporter != null) {
            exporter.stop();
        }
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        exporter = GenAiSpanExporter.startWithProcessor(SimpleSpanProcessor.create(spanExporter));
        int port = server.getLocalPort();
        MockServerClient client = new MockServerClient("localhost", port);
        client.reset();
        client
            .when(request().withMethod("POST").withPath(path))
            .respondWithLlm(llmResponse()
                .withProvider(provider)
                .withModel(model)
                .withCompletion(Completion.completion()
                    .withText("Hello! How can I help?")
                    .withStopReason("stop")
                    .withStreaming(streaming)
                    .withUsage(Usage.usage().withInputTokens(12).withOutputTokens(8))));

        int status = postJson("http://localhost:" + port + path, requestBody);
        assertThat(provider + " " + path + " must be served", status, is(200));

        SpanData span = awaitSingleGenAiSpan(spanExporter);
        assertThat(provider + " " + path, span.getAttributes().get(stringKey("gen_ai.operation.name")), is(expectedOperation));
        assertThat(provider + " " + path, span.getName(), is(expectedOperation + " " + model));
        assertThat(provider + " " + path, span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(12L));
    }

    /**
     * Poll the exporter for up to 5s for exactly one GenAI span (identified by the
     * {@code gen_ai.system} attribute, which only {@code GenAiSpans.recordCompletion} sets — the
     * exporter also receives SERVER request spans from {@code RequestSpans}, which carry no
     * {@code gen_ai.*} attributes). Fails if zero or more than one is emitted.
     */
    private static SpanData awaitSingleGenAiSpan(InMemorySpanExporter spanExporter) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        List<SpanData> genAiSpans;
        do {
            genAiSpans = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getAttributes().get(stringKey("gen_ai.system")) != null)
                .collect(Collectors.toList());
            if (genAiSpans.size() == 1) {
                return genAiSpans.get(0);
            }
            if (genAiSpans.size() > 1) {
                break;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        assertThat("exactly one GenAI span must be emitted by the production serve path",
            genAiSpans.size(), is(1));
        return genAiSpans.get(0);
    }

    private static int postJson(String url, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }
            int status = connection.getResponseCode();
            // drain the response so the exchange (and the server-side span emission) completes
            connection.getInputStream().readAllBytes();
            return status;
        } finally {
            connection.disconnect();
        }
    }
}
