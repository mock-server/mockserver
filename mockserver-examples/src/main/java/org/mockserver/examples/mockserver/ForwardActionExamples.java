package org.mockserver.examples.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.SocketAddress;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.CookiesModifier.cookiesModifier;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HeadersModifier.headersModifier;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpRequestModifier.requestModifier;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpResponseModifier.responseModifier;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.QueryParametersModifier.queryParametersModifier;

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

    public void forwardOverridden() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withPath("/some/other/path")
                        .withHeader("Host", "target.host.com")
                )
            );
    }

    public void forwardOverriddenRequestAndResponse() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withPath("/some/other/path")
                        .withHeader("Host", "target.host.com"),
                    response()
                        .withBody("some_overridden_body")
                )
            );
    }

    public void forwardOverriddenAndModifiedResponse() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        // this replaces the entire set of headers
                        .withHeader("Host", "target.host.com")
                        .withBody("some_overridden_body"),
                    requestModifier()
                        .withPath("^/(.+)/(.+)$", "/prefix/$1/infix/$2/postfix")
                        .withQueryStringParameters(
                            queryParametersModifier()
                                .add(
                                    param("parameterToAddOne", "addedValue"),
                                    param("parameterToAddTwo", "addedValue")
                                )
                                .replace(
                                    param("overrideParameterToReplace", "replacedValue"),
                                    param("requestParameterToReplace", "replacedValue"),
                                    param("extraParameterToReplace", "shouldBeIgnore")
                                )
                                .remove(
                                    "overrideParameterToRemove",
                                    "requestParameterToRemove"
                                )

                        )
                        // this modifies the set of headers
                        .withHeaders(
                            headersModifier()
                                .add(
                                    header("headerToAddOne", "addedValue"),
                                    header("headerToAddTwo", "addedValue")
                                )
                                .replace(
                                    header("overrideHeaderToReplace", "replacedValue"),
                                    header("requestHeaderToReplace", "replacedValue"),
                                    header("extraHeaderToReplace", "shouldBeIgnore")
                                )
                                .remove(
                                    "overrideHeaderToRemove",
                                    "requestHeaderToRemove"
                                )

                        )
                        .withCookies(
                            cookiesModifier()
                                .add(
                                    cookie("cookieToAddOne", "addedValue"),
                                    cookie("cookieToAddTwo", "addedValue")
                                )
                                .replace(
                                    cookie("overrideCookieToReplace", "replacedValue"),
                                    cookie("requestCookieToReplace", "replacedValue"),
                                    cookie("extraCookieToReplace", "shouldBeIgnore")
                                )
                                .remove(
                                    "overrideCookieToRemove",
                                    "requestCookieToRemove"
                                )

                        )
                )
            );
    }

    public void forwardOverriddenAndModifiedRequestAndResponse() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                forwardOverriddenRequest()
                    .withRequestOverride(
                        request()
                            // this replaces the entire set of headers
                            .withHeader("Host", "target.host.com")
                            .withBody("some_overridden_body")
                    )
                    .withRequestModifier(
                        requestModifier()
                            .withPath("^/(.+)/(.+)$", "/prefix/$1/infix/$2/postfix")
                            .withQueryStringParameters(
                                queryParametersModifier()
                                    .add(
                                        param("parameterToAddOne", "addedValue"),
                                        param("parameterToAddTwo", "addedValue")
                                    )
                                    .replace(
                                        param("overrideParameterToReplace", "replacedValue"),
                                        param("requestParameterToReplace", "replacedValue"),
                                        param("extraParameterToReplace", "shouldBeIgnore")
                                    )
                                    .remove(
                                        "overrideParameterToRemove",
                                        "requestParameterToRemove"
                                    )

                            )
                            // this modifies the set of headers
                            .withHeaders(
                                headersModifier()
                                    .add(
                                        header("headerToAddOne", "addedValue"),
                                        header("headerToAddTwo", "addedValue")
                                    )
                                    .replace(
                                        header("overrideHeaderToReplace", "replacedValue"),
                                        header("requestHeaderToReplace", "replacedValue"),
                                        header("extraHeaderToReplace", "shouldBeIgnore")
                                    )
                                    .remove(
                                        "overrideHeaderToRemove",
                                        "requestHeaderToRemove"
                                    )

                            )
                            .withCookies(
                                cookiesModifier()
                                    .add(
                                        cookie("cookieToAddOne", "addedValue"),
                                        cookie("cookieToAddTwo", "addedValue")
                                    )
                                    .replace(
                                        cookie("overrideCookieToReplace", "replacedValue"),
                                        cookie("requestCookieToReplace", "replacedValue"),
                                        cookie("extraCookieToReplace", "shouldBeIgnore")
                                    )
                                    .remove(
                                        "overrideCookieToRemove",
                                        "requestCookieToRemove"
                                    )

                            )
                    )
                    .withResponseOverride(
                        response()
                            .withBody("some_overridden_body")
                    )
                    .withResponseModifier(
                        responseModifier()
                            .withHeaders(
                                headersModifier()
                                    .add(
                                        header("headerToAddOne", "addedValue"),
                                        header("headerToAddTwo", "addedValue")
                                    )
                                    .replace(
                                        header("overrideHeaderToReplace", "replacedValue"),
                                        header("requestHeaderToReplace", "replacedValue"),
                                        header("extraHeaderToReplace", "shouldBeIgnore")
                                    )
                                    .remove(
                                        "overrideHeaderToRemove",
                                        "requestHeaderToRemove"
                                    )

                            )
                            .withCookies(
                                cookiesModifier()
                                    .add(
                                        cookie("cookieToAddOne", "addedValue"),
                                        cookie("cookieToAddTwo", "addedValue")
                                    )
                                    .replace(
                                        cookie("overrideCookieToReplace", "replacedValue"),
                                        cookie("requestCookieToReplace", "replacedValue"),
                                        cookie("extraCookieToReplace", "shouldBeIgnore")
                                    )
                                    .remove(
                                        "overrideCookieToRemove",
                                        "requestCookieToRemove"
                                    )

                            )
                    )
            );
    }

    public void forwardOverriddenWithSocketAddress() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withPath("/some/other/path")
                        .withHeader("Host", "any.host.com")
                        .withSocketAddress("target.host.com", 1234, SocketAddress.Scheme.HTTPS)
                )
            );
    }

    public void forwardOverriddenWithDelay() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "target.host.com")
                        .withBody("some_overridden_body")
                ).withDelay(MILLISECONDS, 10)
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

    public void javascriptTemplatedForwardStripPathPrefix() {
        String template = "return {" + System.getProperty("line.separator") +
            "    'path' : request.path.substring(request.path.indexOf('/foo')+'/foo'.length,request.path.length) ," + System.getProperty("line.separator") +
            "    'headers' : {" + System.getProperty("line.separator") +
            "        'Host' : [ \"localhost:1081\" ]" + System.getProperty("line.separator") +
            "    }," + System.getProperty("line.separator") +
            "};";

        new MockServerClient("localhost", 1080)
            .when(
                request()
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

    public void mustacheTemplatedForward() {
        String template = "{" + System.getProperty("line.separator") +
            "    'path' : \"/somePath\"," + System.getProperty("line.separator") +
            "    'queryStringParameters' : {" + System.getProperty("line.separator") +
            // {{ request.queryStringParameters.userId }} returns an array of values for the 'userId' query parameter
            "        'userId' : [ \"{{ request.queryStringParameters.userId.0 }}\" ]" + System.getProperty("line.separator") +
            "    }," + System.getProperty("line.separator") +
            "    'cookies' : {" + System.getProperty("line.separator") +
            "        'SessionId' : \"{{ request.cookies.SessionId }}\"" + System.getProperty("line.separator") +
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
                    HttpTemplate.TemplateType.MUSTACHE,
                    template
                )
            );
    }
}
