package org.mockserver.netty.integration.mock;

import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.stop.Stop.stopQuietly;

public class GrpcIntegrationTest {

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

    private String sendPutRequest(String path, byte[] body, String contentType) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            String headers = "PUT " + path + " HTTP/1.1\r\n" +
                "Host: localhost:" + mockServerPort + "\r\n" +
                "Connection: close\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "\r\n";
            output.write(headers.getBytes(StandardCharsets.UTF_8));
            output.write(body);
            output.flush();
            return IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8);
        }
    }

    private String sendPutRequest(String path, String body) throws Exception {
        return sendPutRequest(path, body.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private String sendPutRequestNoBody(String path) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            String headers = "PUT " + path + " HTTP/1.1\r\n" +
                "Host: localhost:" + mockServerPort + "\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n";
            output.write(headers.getBytes(StandardCharsets.UTF_8));
            output.flush();
            return IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void shouldUploadGrpcDescriptorViaApi() throws Exception {
        byte[] descriptorBytes = Files.readAllBytes(
            Paths.get("../mockserver-core/src/test/resources/grpc/greeting.dsc")
        );

        String response = sendPutRequest(
            "/mockserver/grpc/descriptors",
            descriptorBytes,
            "application/octet-stream"
        );

        assertThat(response, containsString("201"));
        assertThat(response, containsString("loaded"));
    }

    @Test
    public void shouldListGrpcServicesAfterDescriptorUpload() throws Exception {
        byte[] descriptorBytes = Files.readAllBytes(
            Paths.get("../mockserver-core/src/test/resources/grpc/greeting.dsc")
        );

        sendPutRequest(
            "/mockserver/grpc/descriptors",
            descriptorBytes,
            "application/octet-stream"
        );

        String response = sendPutRequestNoBody("/mockserver/grpc/services");

        assertThat(response, containsString("200"));
        assertThat(response, containsString("com.example.grpc.GreetingService"));
        assertThat(response, containsString("Greeting"));
        assertThat(response, containsString("ListGreetings"));
        assertThat(response, containsString("CollectGreetings"));
        assertThat(response, containsString("Chat"));
    }

    @Test
    public void shouldClearGrpcDescriptors() throws Exception {
        byte[] descriptorBytes = Files.readAllBytes(
            Paths.get("../mockserver-core/src/test/resources/grpc/greeting.dsc")
        );

        sendPutRequest(
            "/mockserver/grpc/descriptors",
            descriptorBytes,
            "application/octet-stream"
        );

        String servicesBeforeClear = sendPutRequestNoBody("/mockserver/grpc/services");
        assertThat(servicesBeforeClear, containsString("com.example.grpc.GreetingService"));

        sendPutRequestNoBody("/mockserver/grpc/clear");

        String servicesAfterClear = sendPutRequestNoBody("/mockserver/grpc/services");
        assertThat(servicesAfterClear, containsString("[ ]"));
    }

    @Test
    public void shouldUploadGrpcDescriptorViaClient() throws Exception {
        byte[] descriptorBytes = Files.readAllBytes(
            Paths.get("../mockserver-core/src/test/resources/grpc/greeting.dsc")
        );

        mockServerClient.uploadGrpcDescriptor(descriptorBytes);

        String services = mockServerClient.retrieveGrpcServices();
        assertThat(services, containsString("com.example.grpc.GreetingService"));
        assertThat(services, containsString("Greeting"));
    }

    @Test
    public void shouldClearGrpcDescriptorsViaClient() throws Exception {
        byte[] descriptorBytes = Files.readAllBytes(
            Paths.get("../mockserver-core/src/test/resources/grpc/greeting.dsc")
        );

        mockServerClient.uploadGrpcDescriptor(descriptorBytes);
        String servicesBefore = mockServerClient.retrieveGrpcServices();
        assertThat(servicesBefore, containsString("com.example.grpc.GreetingService"));

        mockServerClient.clearGrpcDescriptors();
        String servicesAfter = mockServerClient.retrieveGrpcServices();
        assertThat(servicesAfter, containsString("[ ]"));
    }

    @Test
    public void shouldCreateExpectationWithGrpcStreamResponse() throws Exception {
        String expectationJson = "{\n" +
            "  \"httpRequest\": {\n" +
            "    \"method\": \"POST\",\n" +
            "    \"path\": \"/com.example.grpc.GreetingService/Greeting\"\n" +
            "  },\n" +
            "  \"grpcStreamResponse\": {\n" +
            "    \"statusName\": \"OK\",\n" +
            "    \"messages\": [\n" +
            "      {\n" +
            "        \"json\": \"{\\\"greeting\\\": \\\"Hello World\\\"}\"\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";

        String response = sendPutRequest("/mockserver/expectation", expectationJson);
        assertThat(response, containsString("201"));
        assertThat(response, containsString("grpcStreamResponse"));
    }

    @Test
    public void shouldCreateExpectationWithGrpcStreamResponseViaClient() throws Exception {
        mockServerClient
            .when(
                org.mockserver.model.HttpRequest.request()
                    .withMethod("POST")
                    .withPath("/com.example.grpc.GreetingService/Greeting")
            )
            .respondWithGrpcStream(
                org.mockserver.model.GrpcStreamResponse.grpcStreamResponse()
                    .withStatusName("OK")
                    .withMessage("{\"greeting\": \"Hello World\"}")
            );

        String activeExpectations = sendPutRequest(
            "/mockserver/retrieve?type=ACTIVE_EXPECTATIONS",
            ""
        );

        assertThat(activeExpectations, containsString("grpcStreamResponse"));
        assertThat(activeExpectations, containsString("Hello World"));
    }

    @Test
    public void shouldCreateExpectationWithMultipleGrpcStreamMessages() throws Exception {
        String expectationJson = "{\n" +
            "  \"httpRequest\": {\n" +
            "    \"method\": \"POST\",\n" +
            "    \"path\": \"/com.example.grpc.GreetingService/ListGreetings\"\n" +
            "  },\n" +
            "  \"grpcStreamResponse\": {\n" +
            "    \"statusName\": \"OK\",\n" +
            "    \"messages\": [\n" +
            "      {\n" +
            "        \"json\": \"{\\\"greeting\\\": \\\"Hello 1\\\"}\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"json\": \"{\\\"greeting\\\": \\\"Hello 2\\\"}\",\n" +
            "        \"delay\": {\n" +
            "          \"timeUnit\": \"MILLISECONDS\",\n" +
            "          \"value\": 100\n" +
            "        }\n" +
            "      },\n" +
            "      {\n" +
            "        \"json\": \"{\\\"greeting\\\": \\\"Hello 3\\\"}\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"closeConnection\": false\n" +
            "  }\n" +
            "}";

        String response = sendPutRequest("/mockserver/expectation", expectationJson);
        assertThat(response, containsString("201"));

        String activeExpectations = sendPutRequest(
            "/mockserver/retrieve?type=ACTIVE_EXPECTATIONS",
            ""
        );

        assertThat(activeExpectations, containsString("Hello 1"));
        assertThat(activeExpectations, containsString("Hello 2"));
        assertThat(activeExpectations, containsString("Hello 3"));
        assertThat(activeExpectations, containsString("MILLISECONDS"));
    }

    @Test
    public void shouldRejectInvalidGrpcDescriptor() throws Exception {
        String response = sendPutRequest(
            "/mockserver/grpc/descriptors",
            "not a valid descriptor".getBytes(StandardCharsets.UTF_8),
            "application/octet-stream"
        );

        assertThat(response, containsString("400"));
        assertThat(response, containsString("failed to load gRPC descriptor"));
    }

    @Test
    public void shouldRejectEmptyGrpcDescriptor() throws Exception {
        String response = sendPutRequestNoBody("/mockserver/grpc/descriptors");

        assertThat(response, containsString("400"));
    }

    @Test
    public void shouldReturnEmptyServicesWhenNoDescriptorsLoaded() throws Exception {
        String response = sendPutRequestNoBody("/mockserver/grpc/services");

        assertThat(response, containsString("200"));
        assertThat(response, containsString("[ ]"));
    }
}
