package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.A2aMockBuilder;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockserver.client.A2aMockBuilder.a2aMock;
import static org.mockserver.stop.Stop.stopQuietly;

public class A2aMockBuilderIntegrationTest {

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

    private String sendRequest(String httpMethod, String httpPath, String body) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            String request = httpMethod + " " + httpPath + " HTTP/1.1\r\n" +
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

    private String sendJsonRpcRequest(String method, String params, int id) throws Exception {
        String body;
        if (params != null) {
            body = "{\"jsonrpc\": \"2.0\", \"method\": \"" + method + "\", \"params\": " + params + ", \"id\": " + id + "}";
        } else {
            body = "{\"jsonrpc\": \"2.0\", \"method\": \"" + method + "\", \"id\": " + id + "}";
        }
        return sendRequest("POST", "/a2a", body);
    }

    private static void assertJsonRpcId(String response, int expectedId) {
        assertThat(response, containsString("\"id\""));
        String idStr = String.valueOf(expectedId);
        assertThat("Response should contain id value " + idStr, response, containsString(idStr));
    }

    @Test
    public void shouldReturnAgentCard() throws Exception {
        a2aMock()
            .withAgentName("TestAgent")
            .withAgentDescription("A test agent")
            .withAgentVersion("1.0.0")
            .withSkill("translate")
                .withName("Translation")
                .withDescription("Translates text")
                .withTag("i18n")
                .and()
            .applyTo(mockServerClient);

        String response = sendRequest("GET", "/.well-known/agent.json", null);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("TestAgent"));
        assertThat(response, containsString("A test agent"));
        assertThat(response, containsString("translate"));
        assertThat(response, containsString("Translation"));
    }

    @Test
    public void shouldHandleTasksSendRequest() throws Exception {
        a2aMock()
            .withDefaultTaskResponse("Hello from mock agent")
            .applyTo(mockServerClient);

        String params = "{\"message\": {\"role\": \"user\", \"parts\": [{\"type\": \"text\", \"text\": \"Hello\"}]}}";
        String response = sendJsonRpcRequest("tasks/send", params, 1);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("Hello from mock agent"));
        assertJsonRpcId(response, 1);
        assertThat(response, containsString("completed"));
    }

    @Test
    public void shouldHandleTasksGetRequest() throws Exception {
        a2aMock().applyTo(mockServerClient);

        String params = "{\"id\": \"some-task-id\"}";
        String response = sendJsonRpcRequest("tasks/get", params, 2);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertJsonRpcId(response, 2);
    }

    @Test
    public void shouldHandleTasksCancelRequest() throws Exception {
        a2aMock().applyTo(mockServerClient);

        String params = "{\"id\": \"some-task-id\"}";
        String response = sendJsonRpcRequest("tasks/cancel", params, 3);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("canceled"));
        assertJsonRpcId(response, 3);
    }

    @Test
    public void shouldHandleCustomAgentCardPath() throws Exception {
        a2aMock()
            .withAgentCardPath("/my-agent.json")
            .withAgentName("CustomPath")
            .applyTo(mockServerClient);

        String response = sendRequest("GET", "/my-agent.json", null);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("CustomPath"));
    }

    @Test
    public void shouldEchoJsonRpcIdInTaskResponse() throws Exception {
        a2aMock().applyTo(mockServerClient);

        String params = "{\"message\": {\"role\": \"user\", \"parts\": [{\"type\": \"text\", \"text\": \"test\"}]}}";
        String response = sendJsonRpcRequest("tasks/send", params, 42);

        assertJsonRpcId(response, 42);
    }

    @Test
    public void shouldHandleFullA2aWorkflow() throws Exception {
        a2aMock()
            .withAgentName("WorkflowAgent")
            .withSkill("translate")
                .withName("Translation")
                .withDescription("Translates text")
                .and()
            .withDefaultTaskResponse("Task completed")
            .applyTo(mockServerClient);

        String cardResponse = sendRequest("GET", "/.well-known/agent.json", null);
        assertThat(cardResponse, containsString("WorkflowAgent"));
        assertThat(cardResponse, containsString("translate"));

        String sendParams = "{\"message\": {\"role\": \"user\", \"parts\": [{\"type\": \"text\", \"text\": \"Translate hello\"}]}}";
        String sendResponse = sendJsonRpcRequest("tasks/send", sendParams, 1);
        assertThat(sendResponse, containsString("Task completed"));
        assertJsonRpcId(sendResponse, 1);

        String getResponse = sendJsonRpcRequest("tasks/get", "{\"id\": \"mock-task-id\"}", 2);
        assertJsonRpcId(getResponse, 2);

        String cancelResponse = sendJsonRpcRequest("tasks/cancel", "{\"id\": \"mock-task-id\"}", 3);
        assertThat(cancelResponse, containsString("canceled"));
        assertJsonRpcId(cancelResponse, 3);
    }

    @Test
    public void shouldMatchCustomHandlerBeforeDefaultHandler() throws Exception {
        a2aMock()
            .withDefaultTaskResponse("Default response")
            .onTaskSend()
                .matchingMessage("translate.*")
                .respondingWith("Translated: Bonjour")
                .and()
            .applyTo(mockServerClient);

        String params = "{\"message\": {\"role\": \"user\", \"parts\": [{\"type\": \"text\", \"text\": \"translate hello to French\"}]}}";
        String response = sendJsonRpcRequest("tasks/send", params, 10);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("Translated: Bonjour"));
        assertThat(response, not(containsString("Default response")));
        assertJsonRpcId(response, 10);

        String defaultParams = "{\"message\": {\"role\": \"user\", \"parts\": [{\"type\": \"text\", \"text\": \"something else\"}]}}";
        String defaultResponse = sendJsonRpcRequest("tasks/send", defaultParams, 11);

        assertThat(defaultResponse, containsString("HTTP/1.1 200"));
        assertThat(defaultResponse, containsString("Default response"));
        assertJsonRpcId(defaultResponse, 11);
    }

    @Test
    public void shouldHandleVelocityMetacharactersInTaskResponse() throws Exception {
        a2aMock()
            .withDefaultTaskResponse("$100 off #sale")
            .applyTo(mockServerClient);

        String params = "{\"message\": {\"role\": \"user\", \"parts\": [{\"type\": \"text\", \"text\": \"Hello\"}]}}";
        String response = sendJsonRpcRequest("tasks/send", params, 30);

        assertThat(response, containsString("HTTP/1.1 200"));
        assertThat(response, containsString("$100 off #sale"));
        assertJsonRpcId(response, 30);
    }
}
