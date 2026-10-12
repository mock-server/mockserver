package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.client.LlmConversationBuilder.conversation;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.Provider.OPENAI_RESPONSES;
import static org.mockserver.model.ToolUse.toolUse;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * End-to-end test for OpenAI Responses API server-side state driven over a real socket:
 * {@code previous_response_id} chaining, the default {@code store:true} behaviour, and
 * {@code GET /v1/responses/{id}} retrieval.
 *
 * <p>Companion to the handler+store-level {@code OpenAiResponsesStateTest}: this exercises
 * the same three behaviours through a booted {@link MockServer}, asserting on the bytes the
 * client receives over the wire.
 *
 * <p>Chaining is proved via a {@code whenTurnIndex} predicate. {@code turnIndex} counts the
 * ASSISTANT messages in the decoded conversation, so the second-turn expectation
 * ({@code whenTurnIndex(1)}) can only match when the codec has reconstructed the prior
 * turn's assistant reply from {@code previous_response_id} — the second request itself sends
 * only the new user input. If chaining were broken, the decode would carry zero assistant
 * messages and the turn-2 expectation would not match.
 */
public class OpenAiResponsesStateEndToEndTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static int mockServerPort;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void startServer() {
        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Before
    public void resetServer() {
        // reset() clears expectations AND the process-wide OpenAiResponsesStore, so each
        // test starts with no stored responses.
        mockServerClient.reset();
    }

    @Test
    public void chainsViaPreviousResponseIdAndRetrievesStoredResponseOverTheWire() throws Exception {
        // Turn 1 expectation: matches a fresh request (no prior assistant turn -> turnIndex 0).
        conversation()
            .withPath("/v1/responses")
            .withProvider(OPENAI_RESPONSES)
            .withModel("gpt-4o")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion().withText("The capital of France is Paris."))
            .andThen()
            .applyTo(mockServerClient);

        // Turn 2 expectation: matches ONLY once the prior assistant turn has been
        // reconstructed from previous_response_id (turnIndex 1). The turn-2 request body
        // itself carries only the new user input.
        conversation()
            .withPath("/v1/responses")
            .withProvider(OPENAI_RESPONSES)
            .withModel("gpt-4o")
            .turn()
                .whenTurnIndex(1)
                .respondingWith(completion().withText("Paris has about 2.1 million residents."))
            .andThen()
            .applyTo(mockServerClient);

        // --- Turn 1: fresh request, store:true by default -----------------------------
        String turn1Response = sendPost("/v1/responses",
            "{\"model\":\"gpt-4o\",\"input\":\"what is the capital of France?\"}");
        assertThat(turn1Response, containsString("200"));
        JsonNode turn1Node = OBJECT_MAPPER.readTree(extractJsonBody(turn1Response));
        String firstId = turn1Node.path("id").asText();
        assertThat("turn 1 must issue a response id", firstId, startsWith("resp_"));
        assertThat(outputText(turn1Node), containsString("The capital of France is Paris."));

        // --- Turn 2: only new input + previous_response_id ----------------------------
        // A 200 with the turn-2 reply proves the server reconstructed the prior turn from
        // the stored response id: whenTurnIndex(1) cannot match otherwise.
        String turn2Response = sendPost("/v1/responses",
            "{\"model\":\"gpt-4o\",\"input\":[{\"role\":\"user\",\"content\":\"and its population?\"}],"
                + "\"previous_response_id\":\"" + firstId + "\"}");
        assertThat("chained turn must match (previous_response_id reconstruction)",
            turn2Response, containsString("200"));
        JsonNode turn2Node = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        assertThat(outputText(turn2Node), containsString("Paris has about 2.1 million residents."));

        // --- GET /v1/responses/{id}: retrieval of the stored turn-1 response ----------
        String getResponse = sendGet("/v1/responses/" + firstId);
        assertThat("GET of a stored response id must return 200", getResponse, containsString("200"));
        JsonNode retrievedNode = OBJECT_MAPPER.readTree(extractJsonBody(getResponse));
        assertThat("retrieved body must carry the requested id",
            retrievedNode.path("id").asText(), is(firstId));
        assertThat(outputText(retrievedNode), containsString("The capital of France is Paris."));
    }

    @Test
    public void chainsAndRetrievesAStreamedToolCallTurnOverTheWire() throws Exception {
        // Turn 1 streams a tool call whose call_id the test fixes.
        conversation()
            .withPath("/v1/responses")
            .withProvider(OPENAI_RESPONSES)
            .withModel("gpt-4o")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion()
                    .withToolCall(toolUse("get_weather").withId("call_abc123").withArguments("{\"city\":\"Paris\"}"))
                    .withStreaming(true))
            .andThen()
            .applyTo(mockServerClient);
        // Turn 2 matches only once the streamed turn's tool call is reconstructed from
        // previous_response_id: the request itself carries only the function_call_output.
        conversation()
            .withPath("/v1/responses")
            .withProvider(OPENAI_RESPONSES)
            .withModel("gpt-4o")
            .turn()
                .whenContainsToolResultFor("get_weather")
                .respondingWith(completion().withText("It is 18C in Paris."))
            .andThen()
            .applyTo(mockServerClient);

        String streamed = sendPost("/v1/responses",
            "{\"model\":\"gpt-4o\",\"input\":\"weather in Paris?\",\"stream\":true}");
        assertThat(streamed, containsString("200"));
        JsonNode completed = completedResponse(streamed);
        String firstId = completed.path("id").asText();
        assertThat(firstId, startsWith("resp_"));
        assertThat(completed.path("output").path(0).path("call_id").asText(), is("call_abc123"));

        String turn2Response = sendPost("/v1/responses",
            "{\"model\":\"gpt-4o\",\"previous_response_id\":\"" + firstId + "\","
                + "\"input\":[{\"type\":\"function_call_output\",\"call_id\":\"call_abc123\",\"output\":\"18C\"}]}");
        assertThat("chained turn must match the streamed turn's tool call", turn2Response, containsString("200"));
        assertThat(outputText(OBJECT_MAPPER.readTree(extractJsonBody(turn2Response))), containsString("It is 18C in Paris."));

        String getResponse = sendGet("/v1/responses/" + firstId);
        assertThat("GET of a streamed response id must return 200", getResponse, containsString("200"));
        assertThat(OBJECT_MAPPER.readTree(extractJsonBody(getResponse)), is(completed));
    }

    /** The response object of the response.completed event in a raw SSE response. */
    private static JsonNode completedResponse(String rawSse) throws Exception {
        for (String line : dechunked(rawSse).split("\r?\n")) {
            if (line.startsWith("data:")) {
                JsonNode data = OBJECT_MAPPER.readTree(line.substring("data:".length()).trim());
                if ("response.completed".equals(data.path("type").asText())) {
                    return data.path("response");
                }
            }
        }
        throw new AssertionError("no response.completed event in: " + rawSse);
    }

    /** The body of a raw HTTP/1.1 response, with any chunked transfer framing removed. */
    private static String dechunked(String httpResponse) {
        int headerEnd = httpResponse.indexOf("\r\n\r\n");
        String headers = httpResponse.substring(0, headerEnd).toLowerCase(java.util.Locale.ROOT);
        String body = httpResponse.substring(headerEnd + 4);
        if (!headers.contains("transfer-encoding: chunked")) {
            return body;
        }
        StringBuilder content = new StringBuilder();
        int position = 0;
        while (position < body.length()) {
            int lineEnd = body.indexOf("\r\n", position);
            int size = Integer.parseInt(body.substring(position, lineEnd).split(";")[0].trim(), 16);
            if (size == 0) {
                break;
            }
            content.append(body, lineEnd + 2, lineEnd + 2 + size);
            position = lineEnd + 2 + size + 2;
        }
        return content.toString();
    }

    @Test
    public void getForUnknownResponseIdIsNotFoundOverTheWire() throws Exception {
        // No stored responses: an unknown id falls through to the normal 404 path.
        String getResponse = sendGet("/v1/responses/resp_does_not_exist");
        assertThat(getResponse, containsString("404"));
        assertThat(getResponse, not(containsString("200 OK")));
    }

    /** Concatenate all output_text content blocks from a Responses-API body. */
    private static String outputText(JsonNode responseNode) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : responseNode.path("output")) {
            for (JsonNode block : item.path("content")) {
                if ("output_text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText());
                }
            }
        }
        return sb.toString();
    }

    private String sendPost(String path, String body) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            StringBuilder request = new StringBuilder();
            request.append("POST ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: localhost:").append(mockServerPort).append("\r\n");
            request.append("Content-Type: application/json\r\n");
            request.append("Connection: close\r\n");
            request.append("Content-Length: ").append(bodyBytes.length).append("\r\n\r\n");
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            output.write(bodyBytes);
            output.flush();
            return readResponse(socket);
        }
    }

    private String sendGet(String path) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            StringBuilder request = new StringBuilder();
            request.append("GET ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: localhost:").append(mockServerPort).append("\r\n");
            request.append("Connection: close\r\n\r\n");
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            return readResponse(socket);
        }
    }

    private static String readResponse(Socket socket) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = socket.getInputStream().read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    /** Extract the body (after the blank line) from a raw HTTP response string. */
    private static String extractJsonBody(String httpResponse) {
        int bodyStart = httpResponse.indexOf("\r\n\r\n");
        if (bodyStart >= 0) {
            return httpResponse.substring(bodyStart + 4);
        }
        bodyStart = httpResponse.indexOf("\n\n");
        return bodyStart >= 0 ? httpResponse.substring(bodyStart + 2) : httpResponse;
    }
}
