package org.mockserver.examples.mockserver;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.templates.ResponseTemplateTester;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.mockserver.model.HttpRequest.request;

public class ResponseTemplateTesterExamples {

    public void testMustacheResponseTemplate() {
        // inputs
        String template = "{\n" +
            "    'statusCode': 200,\n" +
            "    'body': \"{'method': '{{ request.method }}', 'path': '{{ request.path }}', 'headers': '{{ request.headers.host.0 }}'}\"\n" +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body");

        // execute
        HttpResponse httpResponse = ResponseTemplateTester.testVelocityTemplate(template, request);

        // result
        System.out.println("httpResponse = " + httpResponse);
    }

    public void testVelocityResponseTemplate() {
        // inputs
        String template = "{\n" +
            "    'statusCode': 200,\n" +
            "    'body': \"{'method': '$request.method', 'path': '$request.path', 'headers': '$request.headers.host[0]'}\"\n" +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body");

        // execute
        HttpResponse httpResponse = ResponseTemplateTester.testVelocityTemplate(template, request);

        // result
        System.out.println("httpResponse = " + httpResponse);
    }

    public void testJavaScriptResponseTemplate() {
        // inputs
        String template = "return {\n" +
            "    'statusCode': 200,\n" +
            "    'body': '{\\'method\\': \\'' + request.method + '\\', \\'path\\': \\'' + request.path + '\\', \\'headers\\': \\'' + request.headers.host[0] + '\\'}'\n" +
            "};";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body");

        // execute
        HttpResponse httpResponse = ResponseTemplateTester.testVelocityTemplate(template, request);

        // result
        System.out.println("httpResponse = " + httpResponse);
    }
}
