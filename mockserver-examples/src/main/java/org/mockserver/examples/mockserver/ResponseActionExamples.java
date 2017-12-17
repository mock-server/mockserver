package org.mockserver.examples.mockserver;

import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.HttpTemplate;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_DISPOSITION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class ResponseActionExamples {

    public static void main(String[] args) throws IOException {
        new MockServerClient("localhost", 1080).reset();
        ResponseActionExamples responseActionExamples = new ResponseActionExamples();
        responseActionExamples.responseLiteralWithBodyOnly();
        responseActionExamples.responseLiteralWithUTF16BodyResponse();
        responseActionExamples.responseLiteralWithStatusCodeOnly();
        responseActionExamples.responseLiteralWithBinaryPNGBody();
        responseActionExamples.responseLiteralWith10SecondDelay();
        responseActionExamples.javascriptTemplatedResponse();
        responseActionExamples.javascriptTemplatedResponseWithDelay();
        responseActionExamples.velocityTemplatedResponse();
    }

    public void responseLiteralWithBodyOnly() {
        new MockServerClient("localhost", 1080)
            // this request matcher matches every request
            .when(
                request()
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void responseLiteralWithUTF16BodyResponse() {
        new MockServerClient("localhost", 1080)
            // this request matcher matches every request
            .when(
                request()
            )
            .respond(
                response()
                    .withHeader(
                        CONTENT_TYPE.toString(),
                        MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString()
                    )
                    .withBody("我说中国话".getBytes(Charsets.UTF_16))
            );
    }

    public void responseLiteralWithStatusCodeOnly() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withMethod("POST")
                    .withPath("/some/path")
            )
            .respond(
                response()
                    .withStatusCode(200)
            );
    }

    public void responseLiteralWithBinaryPNGBody() throws IOException {
        byte[] pngBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("org/mockserver/examples/mockserver/test.png"));
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/ws/rest/user/[0-9]+/icon/[0-9]+\\.png")
            )
            .respond(
                response()
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withHeaders(
                        header(CONTENT_TYPE.toString(), MediaType.PNG.toString()),
                        header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\"")
                    )
                    .withBody(binary(pngBytes))
            );
    }

    public void responseLiteralWith10SecondDelay() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                response()
                    .withBody("some_response_body")
                    .withDelay(TimeUnit.SECONDS, 10)
            );
    }

    public void javascriptTemplatedResponse() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                template(
                    HttpTemplate.TemplateType.JAVASCRIPT,
                    "return {" + System.getProperty("line.separator") +
                        "     'statusCode': 200," + System.getProperty("line.separator") +
                        "     'cookies': {" + System.getProperty("line.separator") +
                        "          'session' : request.headers['session-id'][0]" + System.getProperty("line.separator") +
                        "     }," + System.getProperty("line.separator") +
                        "     'headers': {" + System.getProperty("line.separator") +
                        "          'Date' : Date()" + System.getProperty("line.separator") +
                        "     }," + System.getProperty("line.separator") +
                        "     'body': JSON.stringify(" + System.getProperty("line.separator") +
                        "               {" + System.getProperty("line.separator") +
                        "                    method: request.method," + System.getProperty("line.separator") +
                        "                    path: request.path," + System.getProperty("line.separator") +
                        "                    body: request.body" + System.getProperty("line.separator") +
                        "               }" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "};"
                )
            );
    }

    public void javascriptTemplatedResponseWithDelay() {
        String template = "" +
            "if (request.method === 'POST' && request.path === '/somePath') {" + System.getProperty("line.separator") +
            "    return {" + System.getProperty("line.separator") +
            "        'statusCode': 200," + System.getProperty("line.separator") +
            "        'body': JSON.stringify({name: 'value'})" + System.getProperty("line.separator") +
            "    };" + System.getProperty("line.separator") +
            "} else {" + System.getProperty("line.separator") +
            "    return {" + System.getProperty("line.separator") +
            "        'statusCode': 406," + System.getProperty("line.separator") +
            "        'body': request.body" + System.getProperty("line.separator") +
            "    };" + System.getProperty("line.separator") +
            "}";

        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                template(HttpTemplate.TemplateType.JAVASCRIPT)
                    .withTemplate(template)
                    .withDelay(TimeUnit.MINUTES, 2)
            );

    }

    public void velocityTemplatedResponse() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                template(
                    HttpTemplate.TemplateType.VELOCITY,
                    "{" + System.getProperty("line.separator") +
                        "     \"statusCode\": 200," + System.getProperty("line.separator") +
                        "     \"cookies\": { " + System.getProperty("line.separator") +
                        "          \"session\": \"$!request.headers['Session-Id'][0]\"" + System.getProperty("line.separator") +
                        "     }," + System.getProperty("line.separator") +
                        "     \"headers\": {" + System.getProperty("line.separator") +
                        "          \"Client-User-Agent\": [ \"$!request.headers['User-Agent'][0]\" ]" + System.getProperty("line.separator") +
                        "     }," + System.getProperty("line.separator") +
                        "     \"body\": $!request.body" + System.getProperty("line.separator") +
                        "}"
                )
            );
    }
}
