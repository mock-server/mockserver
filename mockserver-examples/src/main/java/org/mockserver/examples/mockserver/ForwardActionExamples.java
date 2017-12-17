package org.mockserver.examples.mockserver;

import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpTemplate;

import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class ForwardActionExamples {

    public void forwardRequestInHTTP() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                forward()
                    .withHost("mock-server.com")
                    .withPort(80)
            );
    }

    public void forwardRequestInHTTPS() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                forward()
                    .withHost("mock-server.com")
                    .withPort(443)
                    .withScheme(HttpForward.Scheme.HTTPS)
            );
    }

    public void javascriptTemplatedForward() {
        String template = "return {" + System.getProperty("line.separator") +
            "    'path' : \"/somePath\"," + System.getProperty("line.separator") +
            "    'queryStringParameters' : {" + System.getProperty("line.separator") +
            // request.queryStringParameters['userId'] returns an array of values for the 'userId' query parameter
            "        'userId' : request.queryStringParameters && request.queryStringParameters['userId']" + System.getProperty("line.separator") +
            "    }," + System.getProperty("line.separator") +
            "    'headers' : {" + System.getProperty("line.separator") +
            "        'Host' : [ \"localhost:1081\" ]" + System.getProperty("line.separator") +
            "    }," + System.getProperty("line.separator") +
            "    'body': JSON.stringify({'name': 'value'})" + System.getProperty("line.separator") +
            "};";

        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                template(
                    HttpTemplate.TemplateType.JAVASCRIPT,
                    template
                )
            );
    }

    public void javascriptTemplatedForwardWithDelay() {
        String template = "return {" + System.getProperty("line.separator") +
            "    'path' : \"/somePath\"," + System.getProperty("line.separator") +
            "    'cookies' : {" + System.getProperty("line.separator") +
            "        'SessionId' : request.cookies && request.cookies['SessionId']" + System.getProperty("line.separator") +
            "    }," + System.getProperty("line.separator") +
            "    'headers' : {" + System.getProperty("line.separator") +
            "        'Host' : [ \"localhost:1081\" ]" + System.getProperty("line.separator") +
            "    }," + System.getProperty("line.separator") +
            "    'keepAlive' : true," + System.getProperty("line.separator") +
            "    'secure' : true," + System.getProperty("line.separator") +
            "    'body' : \"some_body\"" + System.getProperty("line.separator") +
            "};";

        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                template(HttpTemplate.TemplateType.JAVASCRIPT)
                    .withTemplate(template)
                    .withDelay(TimeUnit.SECONDS, 20)
            );
    }

    public void velocityTemplatedForward() {
        String template = "{" + System.getProperty("line.separator") +
            "    'path' : \"/somePath\"," + System.getProperty("line.separator") +
            "    'queryStringParameters' : {" + System.getProperty("line.separator") +
            // $!request.queryStringParameters['userId'] returns an array of values for the 'userId' query parameter
            "        'userId' : [ \"$!request.queryStringParameters['userId'][0]\" ]" + System.getProperty("line.separator") +
            "    }," + System.getProperty("line.separator") +
            "    'cookies' : {" + System.getProperty("line.separator") +
            "        'SessionId' : \"$!request.cookies['SessionId']\"" + System.getProperty("line.separator") +
            "    }," + System.getProperty("line.separator") +
            "    'headers' : {" + System.getProperty("line.separator") +
            "        'Host' : [ \"localhost:1081\" ]" + System.getProperty("line.separator") +
            "    }," + System.getProperty("line.separator") +
            "    'body': \"{'name': 'value'}\"" + System.getProperty("line.separator") +
            "}";

        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                template(
                    HttpTemplate.TemplateType.VELOCITY,
                    template
                )
            );
    }
}
