package org.mockserver.netty.integration.mock;

import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.mockserver.stop.Stop.stopQuietly;

public class SseStreamingIntegrationTest {

    private static MockServerClient mockServerClient;
    private static int mockServerPort;

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

    private void createExpectation(String json) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            output.write(("PUT /mockserver/expectation HTTP/1.1\r\n" +
                "Host: localhost:" + mockServerPort + "\r\n" +
                "Connection: close\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(body);
            output.flush();
            IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8);
        }
    }

    private String readResponse(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        int contentLength = -1;
        boolean chunked = false;

        while ((line = reader.readLine()) != null) {
            response.append(line).append("\n");
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
            if (line.toLowerCase().contains("transfer-encoding: chunked")) {
                chunked = true;
            }
            if (line.isEmpty()) {
                break;
            }
        }

        if (chunked) {
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
        } else if (contentLength > 0) {
            char[] body = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = reader.read(body, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            response.append(new String(body, 0, totalRead));
        }

        return response.toString();
    }

    private String sendSseRequest(String path) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            output.write(("GET " + path + " HTTP/1.1\r\n" +
                "Host: localhost:" + mockServerPort + "\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
            return readResponse(socket);
        }
    }

    private String sendHttpRequest(String method, String path, String requestBody) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            StringBuilder request = new StringBuilder();
            request.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: localhost:").append(mockServerPort).append("\r\n");
            request.append("Connection: close\r\n");
            if (requestBody != null) {
                byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
                request.append("Content-Type: application/json\r\n");
                request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
                request.append("\r\n");
                output.write(request.toString().getBytes(StandardCharsets.UTF_8));
                output.write(bodyBytes);
            } else {
                request.append("Content-Length: 0\r\n");
                request.append("\r\n");
                output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            output.flush();
            return IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void shouldReturnSseStreamWithMultipleEvents() throws Exception {
        createExpectation("{" +
            "\"httpRequest\": {\"method\": \"GET\", \"path\": \"/events\"}," +
            "\"httpSseResponse\": {" +
            "  \"statusCode\": 200," +
            "  \"events\": [" +
            "    {\"event\": \"message\", \"data\": \"hello world\", \"id\": \"1\"}," +
            "    {\"event\": \"update\", \"data\": \"second event\", \"id\": \"2\"}" +
            "  ]," +
            "  \"closeConnection\": true" +
            "}" +
            "}");

        String response = sendSseRequest("/events");

        assertThat(response, containsString("HTTP/1.1 200 OK"));
        assertThat(response, containsString("content-type: text/event-stream"));
        assertThat(response, containsString("transfer-encoding: chunked"));
        assertThat(response, containsString("id: 1"));
        assertThat(response, containsString("event: message"));
        assertThat(response, containsString("data: hello world"));
        assertThat(response, containsString("id: 2"));
        assertThat(response, containsString("event: update"));
        assertThat(response, containsString("data: second event"));
    }

    @Test
    public void shouldReturnSseStreamWithMultiLineData() throws Exception {
        createExpectation("{" +
            "\"httpRequest\": {\"method\": \"GET\", \"path\": \"/multiline\"}," +
            "\"httpSseResponse\": {" +
            "  \"statusCode\": 200," +
            "  \"events\": [" +
            "    {\"event\": \"message\", \"data\": \"line1\\nline2\\nline3\", \"id\": \"1\"}" +
            "  ]," +
            "  \"closeConnection\": true" +
            "}" +
            "}");

        String response = sendSseRequest("/multiline");

        assertThat(response, containsString("HTTP/1.1 200 OK"));
        assertThat(response, containsString("data: line1"));
        assertThat(response, containsString("data: line2"));
        assertThat(response, containsString("data: line3"));
    }

    @Test
    public void shouldReturnSseStreamWithRetryField() throws Exception {
        createExpectation("{" +
            "\"httpRequest\": {\"method\": \"GET\", \"path\": \"/retry\"}," +
            "\"httpSseResponse\": {" +
            "  \"statusCode\": 200," +
            "  \"events\": [" +
            "    {\"event\": \"message\", \"data\": \"retry test\", \"id\": \"1\", \"retry\": 5000}" +
            "  ]," +
            "  \"closeConnection\": true" +
            "}" +
            "}");

        String response = sendSseRequest("/retry");

        assertThat(response, containsString("HTTP/1.1 200 OK"));
        assertThat(response, containsString("retry: 5000"));
        assertThat(response, containsString("data: retry test"));
    }

    @Test
    public void shouldReturnSseStreamWithCustomHeaders() throws Exception {
        createExpectation("{" +
            "\"httpRequest\": {\"method\": \"GET\", \"path\": \"/custom-headers\"}," +
            "\"httpSseResponse\": {" +
            "  \"statusCode\": 200," +
            "  \"headers\": {\"X-Custom\": [\"value\"]}," +
            "  \"events\": [" +
            "    {\"event\": \"message\", \"data\": \"with custom header\", \"id\": \"1\"}" +
            "  ]," +
            "  \"closeConnection\": true" +
            "}" +
            "}");

        String response = sendSseRequest("/custom-headers");

        assertThat(response, containsString("HTTP/1.1 200 OK"));
        assertThat(response, containsString("content-type: text/event-stream"));
        assertThat(response, containsString("X-Custom: value"));
        assertThat(response, containsString("data: with custom header"));
    }

    @Test
    public void shouldReturnSseStreamWithDelayBetweenEvents() throws Exception {
        createExpectation("{" +
            "\"httpRequest\": {\"method\": \"GET\", \"path\": \"/delayed\"}," +
            "\"httpSseResponse\": {" +
            "  \"statusCode\": 200," +
            "  \"events\": [" +
            "    {\"event\": \"first\", \"data\": \"immediate\", \"id\": \"1\"}," +
            "    {\"event\": \"second\", \"data\": \"delayed\", \"id\": \"2\", \"delay\": {\"timeUnit\": \"MILLISECONDS\", \"value\": 200}}" +
            "  ]," +
            "  \"closeConnection\": true" +
            "}" +
            "}");

        long startTime = System.currentTimeMillis();
        String response = sendSseRequest("/delayed");
        long elapsed = System.currentTimeMillis() - startTime;

        assertThat(response, containsString("HTTP/1.1 200 OK"));
        assertThat(response, containsString("data: immediate"));
        assertThat(response, containsString("data: delayed"));
        assertThat(elapsed, is(greaterThanOrEqualTo(150L)));
    }

    @Test
    public void shouldMatchJsonRpcRequestAndReturnResponse() throws Exception {
        createExpectation("{" +
            "\"httpRequest\": {" +
            "  \"method\": \"POST\"," +
            "  \"path\": \"/rpc\"," +
            "  \"body\": {\"type\": \"JSON_RPC\", \"method\": \"tools/list\"}" +
            "}," +
            "\"httpResponse\": {" +
            "  \"statusCode\": 200," +
            "  \"body\": \"{\\\"jsonrpc\\\": \\\"2.0\\\", \\\"result\\\": {\\\"tools\\\": []}, \\\"id\\\": 1}\"" +
            "}" +
            "}");

        String response = sendHttpRequest("POST", "/rpc",
            "{\"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1}");

        assertThat(response, containsString("HTTP/1.1 200 OK"));
        assertThat(response, containsString("\"jsonrpc\": \"2.0\""));
        assertThat(response, containsString("\"tools\": []"));
    }

    @Test
    public void shouldNotMatchJsonRpcRequestWithWrongMethod() throws Exception {
        createExpectation("{" +
            "\"httpRequest\": {" +
            "  \"method\": \"POST\"," +
            "  \"path\": \"/rpc\"," +
            "  \"body\": {\"type\": \"JSON_RPC\", \"method\": \"tools/list\"}" +
            "}," +
            "\"httpResponse\": {" +
            "  \"statusCode\": 200," +
            "  \"body\": \"{\\\"jsonrpc\\\": \\\"2.0\\\", \\\"result\\\": {\\\"tools\\\": []}, \\\"id\\\": 1}\"" +
            "}" +
            "}");

        String response = sendHttpRequest("POST", "/rpc",
            "{\"jsonrpc\": \"2.0\", \"method\": \"resources/list\", \"id\": 1}");

        assertThat(response, containsString("HTTP/1.1 404"));
    }

    @Test
    public void shouldMatchJsonRpcBatchRequest() throws Exception {
        createExpectation("{" +
            "\"httpRequest\": {" +
            "  \"method\": \"POST\"," +
            "  \"path\": \"/rpc\"," +
            "  \"body\": {\"type\": \"JSON_RPC\", \"method\": \"tools/call\"}" +
            "}," +
            "\"httpResponse\": {" +
            "  \"statusCode\": 200," +
            "  \"body\": \"{\\\"jsonrpc\\\": \\\"2.0\\\", \\\"result\\\": {}, \\\"id\\\": 2}\"" +
            "}" +
            "}");

        String response = sendHttpRequest("POST", "/rpc",
            "[{\"jsonrpc\": \"2.0\", \"method\": \"resources/list\", \"id\": 1}," +
                "{\"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"id\": 2}]");

        assertThat(response, containsString("HTTP/1.1 200 OK"));
        assertThat(response, containsString("\"result\": {}"));
    }

    @Test
    public void shouldCreateSseExpectationViaApiAndReturnEvents() throws Exception {
        createExpectation("{" +
            "\"httpRequest\": {\"method\": \"GET\", \"path\": \"/api-events\"}," +
            "\"httpSseResponse\": {" +
            "  \"statusCode\": 200," +
            "  \"events\": [" +
            "    {\"event\": \"init\", \"data\": \"connected\", \"id\": \"100\"}," +
            "    {\"event\": \"data\", \"data\": \"{\\\"key\\\": \\\"value\\\"}\", \"id\": \"101\"}" +
            "  ]," +
            "  \"closeConnection\": true" +
            "}" +
            "}");

        String response = sendSseRequest("/api-events");

        assertThat(response, containsString("HTTP/1.1 200 OK"));
        assertThat(response, containsString("content-type: text/event-stream"));
        assertThat(response, containsString("id: 100"));
        assertThat(response, containsString("event: init"));
        assertThat(response, containsString("data: connected"));
        assertThat(response, containsString("id: 101"));
        assertThat(response, containsString("event: data"));
        assertThat(response, containsString("data: {\"key\": \"value\"}"));
    }
}
