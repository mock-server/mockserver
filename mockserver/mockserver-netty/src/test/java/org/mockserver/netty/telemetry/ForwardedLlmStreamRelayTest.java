package org.mockserver.netty.telemetry;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.llm.client.LlmProviderSniffer;
import org.mockserver.llm.cost.LlmPricing;
import org.mockserver.mock.action.http.LlmCostBudgetMonitor;
import org.mockserver.model.Provider;
import org.mockserver.netty.MockServer;
import org.mockserver.telemetry.GenAiSpanExporter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;

/**
 * End-to-end: a forwarding {@link MockServer} relays the streams of Bedrock (AWS event stream), Gemini
 * ({@code streamGenerateContent} without {@code alt=sse}, a JSON array sent as {@code application/json})
 * and Ollama (newline-delimited JSON, streamed by default) as they arrive, and still counts their usage.
 * <p>
 * The stub upstream sends the first part of its stream and then holds the rest until the client has
 * received that first part, so a proxy that buffers the response can only fail the test.
 * <p>
 * Mutates process-wide state (the GenAI tracer, the cost budget monitor and configuration properties),
 * restored in {@link #tearDown()}; mockserver-netty surefire runs classes one at a time in a single fork.
 */
public class ForwardedLlmStreamRelayTest {

    private static final long HOLD_SECONDS = 10;
    private static final double USD_TOLERANCE = 0.000001;
    private static final String BEDROCK_MODEL = "anthropic.claude-sonnet-4-5-20250929-v1:0";

    private GenAiSpanExporter exporter;
    private InMemorySpanExporter spans;
    private StubUpstream upstream;
    private MockServer forwardServer;
    private String previousLlmProvider;
    private String previousLlmBaseUrl;

    @Before
    public void setUp() {
        previousLlmProvider = ConfigurationProperties.llmProvider();
        previousLlmBaseUrl = ConfigurationProperties.llmBaseUrl();
        LlmCostBudgetMonitor.getInstance().reset();
        spans = InMemorySpanExporter.create();
        exporter = GenAiSpanExporter.startWithProcessor(SimpleSpanProcessor.create(spans));
    }

    @After
    public void tearDown() throws IOException {
        exporter.stop();
        if (forwardServer != null) {
            forwardServer.stop();
        }
        if (upstream != null) {
            upstream.close();
        }
        ConfigurationProperties.llmProvider(previousLlmProvider == null ? "" : previousLlmProvider);
        ConfigurationProperties.llmBaseUrl(previousLlmBaseUrl == null ? "" : previousLlmBaseUrl);
        LlmCostBudgetMonitor.getInstance().reset();
        LlmProviderSniffer.resetConfiguration();
    }

    @Test
    public void shouldRelayABedrockConverseStreamAsItArrivesAndPriceItByTheModelInThePath() throws Exception {
        ConfigurationProperties.llmProvider("BEDROCK");
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        writeEventStreamMessage(first, "messageStart", "{\"role\":\"assistant\"}");
        writeEventStreamMessage(first, "contentBlockDelta", "{\"contentBlockIndex\":0,\"delta\":{\"text\":\"Hello\"}}");
        ByteArrayOutputStream rest = new ByteArrayOutputStream();
        writeEventStreamMessage(rest, "contentBlockStop", "{\"contentBlockIndex\":0}");
        writeEventStreamMessage(rest, "messageStop", "{\"stopReason\":\"end_turn\"}");
        writeEventStreamMessage(rest, "metadata", "{\"metrics\":{\"latencyMs\":623},\"usage\":{\"inputTokens\":25,\"outputTokens\":15,\"totalTokens\":40}}");
        startForwarding(new StubUpstream("application/vnd.amazon.eventstream", first.toByteArray(), rest.toByteArray(), HOLD_SECONDS));

        Relayed relayed = post("/model/anthropic.claude-sonnet-4-5-20250929-v1%3A0/converse-stream",
            "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"Hello\"}]}]}");

        assertRelayedAsItArrived(relayed);
        SpanData span = awaitSingleGenAiSpan();
        assertThat(span.getAttributes().get(stringKey("gen_ai.system")), is("aws.bedrock"));
        assertThat(span.getAttributes().get(stringKey("gen_ai.request.model")), is(BEDROCK_MODEL));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(25L));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.output_tokens")), is(15L));
        assertThat(awaitRecordedCostUsd(), closeTo(LlmPricing.estimateCostUsd(Provider.BEDROCK, BEDROCK_MODEL, 25, 15), USD_TOLERANCE));
    }

    @Test
    public void shouldRelayAGeminiJsonArrayStreamAsItArrives() throws Exception {
        ConfigurationProperties.llmProvider("GEMINI");
        String first = "[{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hel\"}],\"role\":\"model\"},\"index\":0}],"
            + "\"usageMetadata\":{\"promptTokenCount\":8,\"totalTokenCount\":8},\"modelVersion\":\"gemini-2.5-flash\"}";
        String rest = "\r\n,{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"lo\"}],\"role\":\"model\"},\"finishReason\":\"STOP\",\"index\":0}],"
            + "\"usageMetadata\":{\"promptTokenCount\":8,\"candidatesTokenCount\":12,\"totalTokenCount\":20},\"modelVersion\":\"gemini-2.5-flash\"}\r\n]";
        startForwarding(new StubUpstream("application/json; charset=UTF-8", utf8(first), utf8(rest), HOLD_SECONDS));

        Relayed relayed = post("/v1beta/models/gemini-2.5-flash:streamGenerateContent",
            "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Hello\"}]}]}");

        assertRelayedAsItArrived(relayed);
        SpanData span = awaitSingleGenAiSpan();
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(8L));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.output_tokens")), is(12L));
        assertThat(awaitRecordedCostUsd(), closeTo(LlmPricing.estimateCostUsd(Provider.GEMINI, "gemini-2.5-flash", 8, 12), USD_TOLERANCE));
    }

    @Test
    public void shouldRelayAnOllamaChatStreamedByDefaultAsItArrives() throws Exception {
        String first = "{\"model\":\"llama3.2\",\"created_at\":\"2024-12-10T12:00:00.0Z\",\"message\":{\"role\":\"assistant\",\"content\":\"Hel\"},\"done\":false}\n";
        String rest = "{\"model\":\"llama3.2\",\"created_at\":\"2024-12-10T12:00:01.0Z\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},"
            + "\"done_reason\":\"stop\",\"done\":true,\"total_duration\":4883583458,\"prompt_eval_count\":26,\"eval_count\":282}\n";
        upstream = new StubUpstream("application/x-ndjson", utf8(first), utf8(rest), HOLD_SECONDS);
        ConfigurationProperties.llmBaseUrl("http://127.0.0.1:" + upstream.port());
        startForwarding(upstream);

        // no "stream" field: Ollama streams unless it is false
        Relayed relayed = post("/api/chat", "{\"model\":\"llama3.2\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}");

        assertRelayedAsItArrived(relayed);
        SpanData span = awaitSingleGenAiSpan();
        assertThat(span.getAttributes().get(stringKey("gen_ai.system")), is("ollama"));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.input_tokens")), is(26L));
        assertThat(span.getAttributes().get(longKey("gen_ai.usage.output_tokens")), is(282L));
    }

    @Test
    public void shouldStillBufferAPlainJsonResponseThatWasNotAskedToStream() throws Exception {
        ConfigurationProperties.llmProvider("OPENAI");
        String first = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"model\":\"gpt-4o-2024-08-06\",";
        String rest = "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":\"stop\"}],"
            + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}";
        startForwarding(new StubUpstream("application/json", utf8(first), utf8(rest), 1));

        Relayed relayed = post("/v1/chat/completions", "{\"model\":\"gpt-4o\",\"messages\":[]}");

        assertThat("a buffered response cannot reach the client before the upstream ends it", upstream.heldUntilClientRead(), is(false));
        assertThat(relayed.body, is(concat(upstream.first, upstream.rest)));
    }

    private void assertRelayedAsItArrived(Relayed relayed) {
        assertThat("the first part reached the client before the upstream sent the rest", upstream.heldUntilClientRead(), is(true));
        assertThat("the client receives the upstream's bytes unchanged", relayed.body, is(concat(upstream.first, upstream.rest)));
    }

    private void startForwarding(StubUpstream stub) {
        upstream = stub;
        forwardServer = new MockServer();
        new MockServerClient("localhost", forwardServer.getLocalPort())
            .when(request().withMethod("POST"))
            .forward(forward().withHost("127.0.0.1").withPort(upstream.port()));
    }

    /** Reads the first part as soon as it arrives, tells the upstream so, then reads the rest. */
    private Relayed post(String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + forwardServer.getLocalPort() + path).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(utf8(body));
            }
            assertThat(connection.getResponseCode(), is(200));
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                received.write(in.readNBytes(upstream.first.length));
                upstream.clientRead.countDown();
                received.write(in.readAllBytes());
            }
            return new Relayed(received.toByteArray());
        } finally {
            upstream.clientRead.countDown();
            connection.disconnect();
        }
    }

    private SpanData awaitSingleGenAiSpan() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        List<SpanData> genAiSpans;
        do {
            genAiSpans = spans.getFinishedSpanItems().stream()
                .filter(span -> span.getAttributes().get(stringKey("gen_ai.system")) != null)
                .collect(Collectors.toList());
            if (!genAiSpans.isEmpty()) {
                break;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        assertThat("exactly one GenAI span for the streamed forward", genAiSpans.size(), is(1));
        return genAiSpans.get(0);
    }

    private static double awaitRecordedCostUsd() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (LlmCostBudgetMonitor.getInstance().getCumulativeCostUsd() == 0.0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return LlmCostBudgetMonitor.getInstance().getCumulativeCostUsd();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] first, byte[] rest) {
        byte[] all = new byte[first.length + rest.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(rest, 0, all, first.length, rest.length);
        return all;
    }

    /** One AWS event stream message: prelude, prelude CRC, string headers, payload, message CRC. */
    private static void writeEventStreamMessage(ByteArrayOutputStream stream, String eventType, String payloadJson) {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        for (String[] header : new String[][]{{":event-type", eventType}, {":content-type", "application/json"}, {":message-type", "event"}}) {
            byte[] name = utf8(header[0]);
            byte[] value = utf8(header[1]);
            headers.write(name.length);
            headers.write(name, 0, name.length);
            headers.write(7);
            headers.write(value.length >> 8);
            headers.write(value.length & 0xFF);
            headers.write(value, 0, value.length);
        }
        byte[] headerBytes = headers.toByteArray();
        byte[] payload = utf8(payloadJson);
        int totalLength = 12 + headerBytes.length + payload.length + 4;
        ByteBuffer message = ByteBuffer.allocate(totalLength);
        message.putInt(totalLength);
        message.putInt(headerBytes.length);
        message.putInt((int) crc32(message.array(), 8));
        message.put(headerBytes);
        message.put(payload);
        message.putInt((int) crc32(message.array(), totalLength - 4));
        stream.write(message.array(), 0, totalLength);
    }

    private static long crc32(byte[] bytes, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, length);
        return crc.getValue();
    }

    private static final class Relayed {
        private final byte[] body;

        private Relayed(byte[] body) {
            this.body = body;
        }
    }

    /**
     * A one-response HTTP/1.1 upstream that sends its first part as one chunk, then holds the rest
     * until the client has read that part (or the hold times out).
     */
    private static final class StubUpstream {
        private final ServerSocket serverSocket;
        private final byte[] first;
        private final byte[] rest;
        private final CountDownLatch clientRead = new CountDownLatch(1);
        private final AtomicBoolean heldUntilClientRead = new AtomicBoolean();
        private final CountDownLatch responded = new CountDownLatch(1);

        private StubUpstream(String contentType, byte[] first, byte[] rest, long holdSeconds) throws IOException {
            this.first = first;
            this.rest = rest;
            serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            Thread acceptor = new Thread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    readRequest(socket.getInputStream());
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1));
                    writeChunk(out, first);
                    heldUntilClientRead.set(clientRead.await(holdSeconds, TimeUnit.SECONDS));
                    writeChunk(out, rest);
                    out.write("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } catch (IOException | InterruptedException e) {
                    // the socket was closed, by the test or by the proxy
                } finally {
                    responded.countDown();
                }
            }, "stub-llm-stream-upstream");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        boolean heldUntilClientRead() {
            try {
                assertThat("the upstream finished its response", responded.await(HOLD_SECONDS + 5, TimeUnit.SECONDS), is(true));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            return heldUntilClientRead.get();
        }

        void close() throws IOException {
            serverSocket.close();
        }

        private static void readRequest(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            while (!head.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
                int next = in.read();
                if (next == -1) {
                    throw new IOException("request ended before its headers");
                }
                head.write(next);
            }
            int contentLength = 0;
            for (String line : head.toString(StandardCharsets.ISO_8859_1).split("\r\n")) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            if (in.readNBytes(contentLength).length != contentLength) {
                throw new IOException("request ended before its body");
            }
        }

        private static void writeChunk(OutputStream out, byte[] bytes) throws IOException {
            out.write((Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }
    }
}
