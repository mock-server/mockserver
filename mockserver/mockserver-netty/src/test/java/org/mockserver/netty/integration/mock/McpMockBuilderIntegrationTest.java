package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.McpMockBuilder;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockserver.client.McpMockBuilder.mcpMock;
import static org.mockserver.stop.Stop.stopQuietly;

public class McpMockBuilderIntegrationTest {

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
        mockServerClient.reset();
    }

    private String sendJsonRpcRequest(String method, String params, int id) throws Exception {
        return sendJsonRpcRequestWithRawId(method, params, String.valueOf(id));
    }

    private String sendJsonRpcRequestWithRawId(String method, String params, String rawId) throws Exception {
        String body;
        if (params != null) {
            body = "{\"jsonrpc\": \"2.0\", \"method\": \"" + method + "\", \"params\": " + params + ", \"id\": " + rawId + "}";
        } else {
            body = "{\"jsonrpc\": \"2.0\", \"method\": \"" + method + "\", \"id\": " + rawId + "}";
        }
        return sendRequest("POST", "/mcp", body);
    }

    private String sendRequest(String httpMethod, String path, String body) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            String request = httpMethod + " " + path + " HTTP/1.1\r\n" +
                "Host: localhost:" + mockServerPort + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + bodyBytes.length + "\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.UTF_8));
            if (bodyBytes.length > 0) {
                output.write(bodyBytes);
            }
            output.flush();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = socket.getInputStream().read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void assertJsonRpcId(String response, int expectedId) {
        assertThat(response, containsString("\"id\""));
        String idStr = String.valueOf(expectedId);
        assertThat("Response should contain id value " + idStr, response, containsString(idStr));
    }

    @Test
    public void shouldHandleInitializeRequest() throws Exception {
        mcpMock()
            .withServerName("TestMCP")
            .withServerVersion("1.0.0")
            .withToolsCapability()
            .applyTo(mockServerClient);

        String response = sendJsonRpcRequest("initialize", null, 1);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("\"jsonrpc\""));
        assertThat(response, containsString("\"result\""));
        assertThat(response, containsString("TestMCP"));
        assertThat(response, containsString("\"protocolVersion\""));
        assertThat(response, containsString("\"capabilities\""));
        assertThat(response, containsString("\"tools\""));
        assertJsonRpcId(response, 1);
    }

    @Test
    public void shouldEchoJsonRpcIdInResponse() throws Exception {
        mcpMock().applyTo(mockServerClient);

        String response = sendJsonRpcRequest("ping", null, 42);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertJsonRpcId(response, 42);
    }

    @Test
    public void shouldHandlePingRequest() throws Exception {
        mcpMock().applyTo(mockServerClient);

        String response = sendJsonRpcRequest("ping", null, 5);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("\"result\""));
        assertJsonRpcId(response, 5);
    }

    @Test
    public void shouldHandleToolsListRequest() throws Exception {
        mcpMock()
            .withTool("get_weather")
                .withDescription("Get weather for a city")
                .withInputSchema("{\"type\": \"object\", \"properties\": {\"city\": {\"type\": \"string\"}}}")
                .respondingWith("72F")
                .and()
            .withTool("search")
                .withDescription("Search the knowledge base")
                .respondingWith("No results")
                .and()
            .applyTo(mockServerClient);

        String response = sendJsonRpcRequest("tools/list", null, 3);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("get_weather"));
        assertThat(response, containsString("Get weather for a city"));
        assertThat(response, containsString("search"));
        assertJsonRpcId(response, 3);
    }

    @Test
    public void shouldHandleToolsCallRequest() throws Exception {
        mcpMock()
            .withTool("get_weather")
                .withDescription("Get weather")
                .respondingWith("72F and sunny")
                .and()
            .applyTo(mockServerClient);

        String response = sendJsonRpcRequest("tools/call", "{\"name\": \"get_weather\", \"arguments\": {\"city\": \"London\"}}", 7);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("72F and sunny"));
        assertJsonRpcId(response, 7);
    }

    @Test
    public void shouldHandleResourcesListRequest() throws Exception {
        mcpMock()
            .withResource("config://app")
                .withName("App Config")
                .withDescription("Application configuration")
                .withMimeType("application/json")
                .withContent("{\"debug\": true}")
                .and()
            .applyTo(mockServerClient);

        String response = sendJsonRpcRequest("resources/list", null, 4);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("config://app"));
        assertThat(response, containsString("App Config"));
        assertJsonRpcId(response, 4);
    }

    @Test
    public void shouldHandleResourcesReadRequest() throws Exception {
        mcpMock()
            .withResource("config://app")
                .withName("App Config")
                .withMimeType("application/json")
                .withContent("{\"debug\": true}")
                .and()
            .applyTo(mockServerClient);

        String response = sendJsonRpcRequest("resources/read", "{\"uri\": \"config://app\"}", 8);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("config://app"));
        assertJsonRpcId(response, 8);
    }

    @Test
    public void shouldHandlePromptsListRequest() throws Exception {
        mcpMock()
            .withPrompt("code_review")
                .withDescription("Review code changes")
                .withArgument("language", "Programming language", true)
                .respondingWith("user", "Review this code")
                .and()
            .applyTo(mockServerClient);

        String response = sendJsonRpcRequest("prompts/list", null, 9);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("code_review"));
        assertThat(response, containsString("Review code changes"));
        assertJsonRpcId(response, 9);
    }

    @Test
    public void shouldHandlePromptsGetRequest() throws Exception {
        mcpMock()
            .withPrompt("code_review")
                .withDescription("Review code changes")
                .respondingWith("user", "Please review this code")
                .respondingWith("assistant", "I will review the code now.")
                .and()
            .applyTo(mockServerClient);

        String response = sendJsonRpcRequest("prompts/get", "{\"name\": \"code_review\"}", 10);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("Please review this code"));
        assertJsonRpcId(response, 10);
    }

    @Test
    public void shouldHandleToolErrorResponse() throws Exception {
        mcpMock()
            .withTool("failing_tool")
                .respondingWith("Something went wrong", true)
                .and()
            .applyTo(mockServerClient);

        String response = sendJsonRpcRequest("tools/call", "{\"name\": \"failing_tool\"}", 11);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("Something went wrong"));
        assertThat(response, containsString("isError"));
        assertThat(response, containsString("true"));
        assertJsonRpcId(response, 11);
    }

    @Test
    public void shouldHandleCustomPath() throws Exception {
        mcpMock("/api/v1/mcp")
            .withServerName("CustomPath")
            .applyTo(mockServerClient);

        String body = "{\"jsonrpc\": \"2.0\", \"method\": \"ping\", \"id\": 99}";
        String response = sendRequest("POST", "/api/v1/mcp", body);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertJsonRpcId(response, 99);
    }

    @Test
    public void shouldHandleFullMcpWorkflow() throws Exception {
        mcpMock()
            .withServerName("FullWorkflow")
            .withServerVersion("1.0.0")
            .withTool("calculator")
                .withDescription("Basic calculator")
                .respondingWith("42")
                .and()
            .withResource("data://numbers")
                .withName("Numbers")
                .withContent("[1, 2, 3]")
                .and()
            .applyTo(mockServerClient);

        String initResponse = sendJsonRpcRequest("initialize", null, 1);
        assertThat(initResponse, containsString("FullWorkflow"));
        assertJsonRpcId(initResponse, 1);

        String notifBody = "{\"jsonrpc\": \"2.0\", \"method\": \"notifications/initialized\"}";
        String notifResponse = sendRequest("POST", "/mcp", notifBody);
        assertThat(notifResponse, containsString("HTTP/1.1 200"));

        String toolsResponse = sendJsonRpcRequest("tools/list", null, 2);
        assertThat(toolsResponse, containsString("calculator"));
        assertJsonRpcId(toolsResponse, 2);

        String callResponse = sendJsonRpcRequest("tools/call", "{\"name\": \"calculator\", \"arguments\": {\"op\": \"add\"}}", 3);
        assertThat(callResponse, containsString("42"));
        assertJsonRpcId(callResponse, 3);

        String resResponse = sendJsonRpcRequest("resources/list", null, 4);
        assertThat(resResponse, containsString("data://numbers"));
        assertJsonRpcId(resResponse, 4);

        String readResponse = sendJsonRpcRequest("resources/read", "{\"uri\": \"data://numbers\"}", 5);
        assertJsonRpcId(readResponse, 5);

        String pingResponse = sendJsonRpcRequest("ping", null, 6);
        assertJsonRpcId(pingResponse, 6);
    }

    @Test
    public void shouldEchoStringJsonRpcId() throws Exception {
        mcpMock().applyTo(mockServerClient);

        String response = sendJsonRpcRequestWithRawId("ping", null, "\"abc-123\"");

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("abc-123"));
        assertThat(response, containsString("\"id\""));
    }

    @Test
    public void shouldHandleVelocityMetacharactersInToolResponse() throws Exception {
        mcpMock()
            .withTool("pricing")
                .withDescription("Get pricing info")
                .respondingWith("$100 off #sale")
                .and()
            .applyTo(mockServerClient);

        String response = sendJsonRpcRequest("tools/call", "{\"name\": \"pricing\", \"arguments\": {}}", 20);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("$100 off #sale"));
        assertJsonRpcId(response, 20);
    }
}
