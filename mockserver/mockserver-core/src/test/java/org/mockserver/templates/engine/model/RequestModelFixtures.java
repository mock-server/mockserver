package org.mockserver.templates.engine.model;

import org.mockserver.model.HttpRequest;

import java.nio.charset.StandardCharsets;

import static org.mockserver.model.HttpRequest.request;

/**
 * Requests shared by the request-model template tests of every engine.
 */
public final class RequestModelFixtures {

    public static final String BOUNDARY = "TemplateBoundary7MA4YWxk";

    private RequestModelFixtures() {
    }

    public static HttpRequest formRequest(String body) {
        return request()
            .withMethod("POST")
            .withPath("/form")
            .withHeader("Content-Type", "application/x-www-form-urlencoded")
            .withBody(body);
    }

    /**
     * A multipart/form-data request with a text field {@code title=hello} and a file part {@code file}
     * named {@code notes.txt} of type {@code text/plain} holding {@code abc}.
     */
    public static HttpRequest multipartRequest() {
        String body = "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"title\"\r\n"
            + "\r\n"
            + "hello\r\n"
            + "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"notes.txt\"\r\n"
            + "Content-Type: text/plain\r\n"
            + "\r\n"
            + "abc\r\n"
            + "--" + BOUNDARY + "--\r\n";
        return multipartRequest(body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpRequest multipartRequest(byte[] body) {
        return request()
            .withMethod("POST")
            .withPath("/upload")
            .withHeader("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .withBody(body);
    }

    /**
     * A well-formed multipart/form-data body with one file part of {@code contentBytes} bytes.
     */
    public static byte[] multipartBodyWithFileOf(int contentBytes) {
        byte[] head = ("--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"big.bin\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + contentBytes + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        java.util.Arrays.fill(body, head.length, head.length + contentBytes, (byte) 'x');
        System.arraycopy(tail, 0, body, head.length + contentBytes, tail.length);
        return body;
    }

    public static HttpRequest multipartRequestWithParts(int partCount) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < partCount; i++) {
            body.append("--").append(BOUNDARY).append("\r\n")
                .append("Content-Disposition: form-data; name=\"field").append(i).append("\"\r\n\r\n")
                .append("value").append(i).append("\r\n");
        }
        body.append("--").append(BOUNDARY).append("--\r\n");
        return multipartRequest(body.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static HttpRequest addressedRequest() {
        HttpRequest request = request()
            .withMethod("GET")
            .withPath("/where")
            .withHeader("Host", "api.example.com:8443")
            .withSecure(true)
            .withBody("abc");
        request.withLogCorrelationId("3f2a6c1e-request-id");
        return request;
    }
}
