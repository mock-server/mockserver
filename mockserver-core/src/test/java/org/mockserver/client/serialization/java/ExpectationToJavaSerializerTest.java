package org.mockserver.client.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.TimeToLive.unlimited;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class ExpectationToJavaSerializerTest {

    private final Base64Converter base64Converter = new Base64Converter();

    @Test
    public void shouldSerializeArrayOfObjectsAsJava() {
        assertEquals(NEW_LINE +
                "new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                ".when(" + NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withPath(\"somePathOne\"),\n" +
                "        Times.once()" + NEW_LINE +
                ")" + NEW_LINE +
                ".respond(" + NEW_LINE +
                "        response()" + NEW_LINE +
                "                .withStatusCode(200)" + NEW_LINE +
                "                .withReasonPhrase(\"OK\")" + NEW_LINE +
                "                .withBody(\"responseBodyOne\")" + NEW_LINE +
                ");" + NEW_LINE +
                NEW_LINE +
                NEW_LINE +
                "new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                ".when(" + NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withPath(\"somePathOne\")," + NEW_LINE +
                "        Times.exactly(2)," + NEW_LINE +
                "        TimeToLive.exactly(TimeUnit.MINUTES, 1L)" + NEW_LINE +
                ")" + NEW_LINE +
                ".respond(" + NEW_LINE +
                "        response()" + NEW_LINE +
                "                .withStatusCode(200)" + NEW_LINE +
                "                .withReasonPhrase(\"OK\")" + NEW_LINE +
                "                .withBody(\"responseBodyOne\")" + NEW_LINE +
                ");" + NEW_LINE +
                NEW_LINE,
            new ExpectationToJavaSerializer().serialize(
                Arrays.asList(
                    new Expectation(
                        request("somePathOne"),
                        once(),
                        null
                    )
                        .thenRespond(
                            response("responseBodyOne")
                        ),
                    new Expectation(
                        request("somePathOne"),
                        exactly(2),
                        TimeToLive.exactly(TimeUnit.MINUTES, 1L)
                    )
                        .thenRespond(
                            response("responseBodyOne")
                        )
                )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithResponseAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withMethod(\"GET\")" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withQueryStringParameters(" + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .respond(" + NEW_LINE +
                "                response()" + NEW_LINE +
                "                        .withStatusCode(304)" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"responseHeaderNameOne\", \"responseHeaderValueOneOne\", \"responseHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"responseHeaderNameTwo\", \"responseHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"responseCookieNameOne\", \"responseCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"responseCookieNameTwo\", \"responseCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(\"responseBody\")" + NEW_LINE +
                "                        .withDelay(new Delay(TimeUnit.MINUTES, 1))" + NEW_LINE +
                "                        .withConnectionOptions(" + NEW_LINE +
                "                                connectionOptions()" + NEW_LINE +
                "                                        .withSuppressContentLengthHeader(true)" + NEW_LINE +
                "                                        .withContentLengthHeaderOverride(10)" + NEW_LINE +
                "                                        .withSuppressConnectionHeader(true)" + NEW_LINE +
                "                                        .withKeepAliveOverride(true)" + NEW_LINE +
                "                                        .withCloseSocket(true)" + NEW_LINE +
                "                        )" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("somePath")
                        .withQueryStringParameters(
                            new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                            new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                        )
                        .withHeaders(
                            new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                            new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                            new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        )
                        .withBody(new StringBody("somebody")),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        response()
                            .withStatusCode(304)
                            .withHeaders(
                                new Header("responseHeaderNameOne", "responseHeaderValueOneOne", "responseHeaderValueOneTwo"),
                                new Header("responseHeaderNameTwo", "responseHeaderValueTwo")
                            )
                            .withCookies(
                                new Cookie("responseCookieNameOne", "responseCookieValueOne"),
                                new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                            )
                            .withBody("responseBody")
                            .withDelay(new Delay(TimeUnit.MINUTES, 1))
                            .withConnectionOptions(
                                connectionOptions()
                                    .withSuppressContentLengthHeader(true)
                                    .withContentLengthHeaderOverride(10)
                                    .withSuppressConnectionHeader(true)
                                    .withKeepAliveOverride(true)
                                    .withCloseSocket(true)
                            )
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithResponseTemplateAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withMethod(\"GET\")" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withQueryStringParameters(" + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .respond(" + NEW_LINE +
                "                template(HttpTemplate.TemplateType.JAVASCRIPT)" + NEW_LINE +
                "                        .withTemplate(\"if (request.method === 'POST' && request.path === '/somePath') {\\n    return {\\n        'statusCode': 200,\\n        'body': JSON.stringify({name: 'value'})\\n    };\\n} else {\\n    return {\\n        'statusCode': 406,\\n        'body': request.body\\n    };\\n}\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("somePath")
                        .withQueryStringParameters(
                            new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                            new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                        )
                        .withHeaders(
                            new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                            new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                            new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        )
                        .withBody(new StringBody("somebody")),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        template(HttpTemplate.TemplateType.JAVASCRIPT, "if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
                            "    return {" + NEW_LINE +
                            "        'statusCode': 200," + NEW_LINE +
                            "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
                            "    };" + NEW_LINE +
                            "} else {" + NEW_LINE +
                            "    return {" + NEW_LINE +
                            "        'statusCode': 406," + NEW_LINE +
                            "        'body': request.body" + NEW_LINE +
                            "    };" + NEW_LINE +
                            "}"
                        )
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithParameterBodyResponseAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withBody(" + NEW_LINE +
                "                                new ParameterBody(" + NEW_LINE +
                "                                        new Parameter(\"requestBodyParameterNameOne\", \"requestBodyParameterValueOneOne\", \"requestBodyParameterValueOneTwo\")," + NEW_LINE +
                "                                        new Parameter(\"requestBodyParameterNameTwo\", \"requestBodyParameterValueTwo\")" + NEW_LINE +
                "                                )" + NEW_LINE +
                "                        )," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .respond(" + NEW_LINE +
                "                response()" + NEW_LINE +
                "                        .withBody(\"responseBody\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withBody(
                            new ParameterBody(
                                new Parameter("requestBodyParameterNameOne", "requestBodyParameterValueOneOne", "requestBodyParameterValueOneTwo"),
                                new Parameter("requestBodyParameterNameTwo", "requestBodyParameterValueTwo")
                            )
                        ),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        response()
                            .withBody("responseBody")
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithBinaryBodyResponseAsJava() {
        // when
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withBody(new Base64Converter().base64StringToBytes(\"" + base64Converter.bytesToBase64String("request body".getBytes(UTF_8)) + "\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .respond(" + NEW_LINE +
                "                response()" + NEW_LINE +
                "                        .withBody(\"responseBody\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withBody(
                            new BinaryBody("request body".getBytes(UTF_8))
                        ),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        response()
                            .withBody("responseBody")
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithResponseClassCallbackAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withMethod(\"GET\")" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withQueryStringParameters(" + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .respond(" + NEW_LINE +
                "                callback()" + NEW_LINE +
                "                        .withCallbackClass(\"some_class\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("somePath")
                        .withQueryStringParameters(
                            new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                            new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                        )
                        .withHeaders(
                            new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                            new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                            new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        )
                        .withBody(new StringBody("somebody")),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        callback()
                            .withCallbackClass("some_class")
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithResponseObjectCallbackAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withMethod(\"GET\")" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withQueryStringParameters(" + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        /*NOT POSSIBLE TO GENERATE CODE FOR OBJECT CALLBACK*/;",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("somePath")
                        .withQueryStringParameters(
                            new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                            new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                        )
                        .withHeaders(
                            new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                            new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                            new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        )
                        .withBody(new StringBody("somebody")),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        new HttpObjectCallback()
                            .withClientId("some_client_id")
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withMethod(\"GET\")" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withQueryStringParameters(" + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .forward(" + NEW_LINE +
                "                forward()" + NEW_LINE +
                "                        .withHost(\"some_host\")" + NEW_LINE +
                "                        .withPort(9090)" + NEW_LINE +
                "                        .withScheme(HttpForward.Scheme.HTTPS)" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("somePath")
                        .withQueryStringParameters(
                            new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                            new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                        )
                        .withHeaders(
                            new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                            new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                            new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        )
                        .withBody(new StringBody("somebody")),
                    once(),
                    unlimited()
                )
                    .thenForward(
                        forward()
                            .withHost("some_host")
                            .withPort(9090)
                            .withScheme(HttpForward.Scheme.HTTPS)
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithForwardTemplateAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withMethod(\"GET\")" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withQueryStringParameters(" + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .forward(" + NEW_LINE +
                "                template(HttpTemplate.TemplateType.JAVASCRIPT)" + NEW_LINE +
                "                        .withTemplate(\"return { 'path': \\\"somePath\\\", 'body': JSON.stringify({name: 'value'}) };\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("somePath")
                        .withQueryStringParameters(
                            new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                            new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                        )
                        .withHeaders(
                            new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                            new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                            new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        )
                        .withBody(new StringBody("somebody")),
                    once(),
                    unlimited()
                )
                    .thenForward(
                        template(HttpTemplate.TemplateType.JAVASCRIPT)
                            .withTemplate("return { 'path': \"somePath\", 'body': JSON.stringify({name: 'value'}) };")
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithForwardClassCallbackAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withMethod(\"GET\")" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withQueryStringParameters(" + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .forward(" + NEW_LINE +
                "                callback()" + NEW_LINE +
                "                        .withCallbackClass(\"some_class\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("somePath")
                        .withQueryStringParameters(
                            new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                            new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                        )
                        .withHeaders(
                            new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                            new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                            new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        )
                        .withBody(new StringBody("somebody")),
                    once(),
                    unlimited()
                )
                    .thenForward(
                        callback()
                            .withCallbackClass("some_class")
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithForwardObjectCallbackAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withMethod(\"GET\")" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withQueryStringParameters(" + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        /*NOT POSSIBLE TO GENERATE CODE FOR OBJECT CALLBACK*/;",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("somePath")
                        .withQueryStringParameters(
                            new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                            new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                        )
                        .withHeaders(
                            new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                            new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                            new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        )
                        .withBody(new StringBody("somebody")),
                    once(),
                    unlimited()
                )
                    .thenForward(
                        new HttpObjectCallback()
                            .withClientId("some_client_id")
                    )
            )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithErrorAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withMethod(\"GET\")" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withHeaders(" + NEW_LINE +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withCookies(" + NEW_LINE +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withQueryStringParameters(" + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .error(" + NEW_LINE +
                "                error()" + NEW_LINE +
                "                        .withDelay(new Delay(TimeUnit.MINUTES, 1))" + NEW_LINE +
                "                        .withDropConnection(true)" + NEW_LINE +
                "                        .withResponseBytes(new Base64Converter().base64StringToBytes(\"" + base64Converter.bytesToBase64String("some_bytes".getBytes(UTF_8)) + "\"))" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("somePath")
                        .withQueryStringParameters(
                            new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                            new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                        )
                        .withHeaders(
                            new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                            new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                            new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        )
                        .withBody(new StringBody("somebody")),
                    once(),
                    unlimited()
                )
                    .thenError(
                        error()
                            .withDelay(new Delay(TimeUnit.MINUTES, 1))
                            .withDropConnection(true)
                            .withResponseBytes("some_bytes".getBytes(UTF_8))
                    )
            )
        );
    }

    @Test
    public void shouldEscapeJsonBodies() {
        assertEquals("" + NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withBody(new JsonBody(\"[" + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }," + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }," + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }" + StringEscapeUtils.escapeJava(NEW_LINE) + "]\", JsonBodyMatchType.ONLY_MATCHING_FIELDS))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .respond(" + NEW_LINE +
                "                response()" + NEW_LINE +
                "                        .withStatusCode(304)" + NEW_LINE +
                "                        .withBody(\"[" + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }," + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }," + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }" + StringEscapeUtils.escapeJava(NEW_LINE) + "]\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withPath("somePath")
                        .withBody(new JsonBody("[" + NEW_LINE +
                            "    {" + NEW_LINE +
                            "        \"id\": \"1\"," + NEW_LINE +
                            "        \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\"," + NEW_LINE +
                            "        \"author\": \"James Tatum\"," + NEW_LINE +
                            "        \"isbn\": \"0691067570\"," + NEW_LINE +
                            "        \"publicationDate\": \"1989\"" + NEW_LINE +
                            "    }," + NEW_LINE +
                            "    {" + NEW_LINE +
                            "        \"id\": \"2\"," + NEW_LINE +
                            "        \"title\": \"You are here : personal geographies and other maps of the imagination\"," + NEW_LINE +
                            "        \"author\": \"Katharine A. Harmon\"," + NEW_LINE +
                            "        \"isbn\": \"1568984308\"," + NEW_LINE +
                            "        \"publicationDate\": \"2004\"" + NEW_LINE +
                            "    }," + NEW_LINE +
                            "    {" + NEW_LINE +
                            "        \"id\": \"3\"," + NEW_LINE +
                            "        \"title\": \"You just don't understand : women and men in conversation\"," + NEW_LINE +
                            "        \"author\": \"Deborah Tannen\"," + NEW_LINE +
                            "        \"isbn\": \"0345372050\"," + NEW_LINE +
                            "        \"publicationDate\": \"1990\"" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "]")),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        response()
                            .withStatusCode(304)
                            .withBody("[" + NEW_LINE +
                                "    {" + NEW_LINE +
                                "        \"id\": \"1\"," + NEW_LINE +
                                "        \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\"," + NEW_LINE +
                                "        \"author\": \"James Tatum\"," + NEW_LINE +
                                "        \"isbn\": \"0691067570\"," + NEW_LINE +
                                "        \"publicationDate\": \"1989\"" + NEW_LINE +
                                "    }," + NEW_LINE +
                                "    {" + NEW_LINE +
                                "        \"id\": \"2\"," + NEW_LINE +
                                "        \"title\": \"You are here : personal geographies and other maps of the imagination\"," + NEW_LINE +
                                "        \"author\": \"Katharine A. Harmon\"," + NEW_LINE +
                                "        \"isbn\": \"1568984308\"," + NEW_LINE +
                                "        \"publicationDate\": \"2004\"" + NEW_LINE +
                                "    }," + NEW_LINE +
                                "    {" + NEW_LINE +
                                "        \"id\": \"3\"," + NEW_LINE +
                                "        \"title\": \"You just don't understand : women and men in conversation\"," + NEW_LINE +
                                "        \"author\": \"Deborah Tannen\"," + NEW_LINE +
                                "        \"isbn\": \"0345372050\"," + NEW_LINE +
                                "        \"publicationDate\": \"1990\"" + NEW_LINE +
                                "    }" + NEW_LINE +
                                "]")
                    )
            )
        );
    }

    @Test
    public void shouldEscapeJsonSchemaBodies() {
        String jsonSchema = "{" + NEW_LINE +
            "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
            "    \"title\": \"Product\"," + NEW_LINE +
            "    \"description\": \"A product from Acme's catalog\"," + NEW_LINE +
            "    \"type\": \"object\"," + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"id\": {" + NEW_LINE +
            "            \"description\": \"The unique identifier for a product\"," + NEW_LINE +
            "            \"type\": \"integer\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\": {" + NEW_LINE +
            "            \"description\": \"Name of the product\"," + NEW_LINE +
            "            \"type\": \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"price\": {" + NEW_LINE +
            "            \"type\": \"number\"," + NEW_LINE +
            "            \"minimum\": 0," + NEW_LINE +
            "            \"exclusiveMinimum\": true" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tags\": {" + NEW_LINE +
            "            \"type\": \"array\"," + NEW_LINE +
            "            \"items\": {" + NEW_LINE +
            "                \"type\": \"string\"" + NEW_LINE +
            "            }," + NEW_LINE +
            "            \"minItems\": 1," + NEW_LINE +
            "            \"uniqueItems\": true" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
            "}";
        assertEquals("" + NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withBody(new JsonSchemaBody(\"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .respond(" + NEW_LINE +
                "                response()" + NEW_LINE +
                "                        .withStatusCode(304)" + NEW_LINE +
                "                        .withBody(\"responseBody\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withPath("somePath")
                        .withBody(new JsonSchemaBody(jsonSchema)),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        response()
                            .withStatusCode(304)
                            .withBody("responseBody")
                    )
            )
        );
    }

    @Test
    public void shouldEscapeXmlSchemaBodies() {
        String xmlSchema = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
            "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
            "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
            "    <xs:element name=\"notes\">" + NEW_LINE +
            "        <xs:complexType>" + NEW_LINE +
            "            <xs:sequence>" + NEW_LINE +
            "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
            "                    <xs:complexType>" + NEW_LINE +
            "                        <xs:sequence>" + NEW_LINE +
            "                            <xs:element name=\"to\" type=\"xs:string\"></xs:element>" + NEW_LINE +
            "                            <xs:element name=\"from\" type=\"xs:string\"></xs:element>" + NEW_LINE +
            "                            <xs:element name=\"heading\" type=\"xs:string\"></xs:element>" + NEW_LINE +
            "                            <xs:element name=\"body\" type=\"xs:string\"></xs:element>" + NEW_LINE +
            "                        </xs:sequence>" + NEW_LINE +
            "                    </xs:complexType>" + NEW_LINE +
            "                </xs:element>" + NEW_LINE +
            "            </xs:sequence>" + NEW_LINE +
            "        </xs:complexType>" + NEW_LINE +
            "    </xs:element>" + NEW_LINE +
            "</xs:schema>";
        assertEquals("" + NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withBody(new XmlSchemaBody(\"" + StringEscapeUtils.escapeJava(xmlSchema) + "\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .respond(" + NEW_LINE +
                "                response()" + NEW_LINE +
                "                        .withStatusCode(304)" + NEW_LINE +
                "                        .withBody(\"responseBody\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withPath("somePath")
                        .withBody(new XmlSchemaBody(xmlSchema)),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        response()
                            .withStatusCode(304)
                            .withBody("responseBody")
                    )
            )
        );
    }

    @Test
    public void shouldSerializeMinimalObjectAsJava() {
        assertEquals(NEW_LINE +
                "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                "        .when(" + NEW_LINE +
                "                request()" + NEW_LINE +
                "                        .withPath(\"somePath\")" + NEW_LINE +
                "                        .withBody(new StringBody(\"responseBody\"))," + NEW_LINE +
                "                Times.once()," + NEW_LINE +
                "                TimeToLive.unlimited()" + NEW_LINE +
                "        )" + NEW_LINE +
                "        .respond(" + NEW_LINE +
                "                response()" + NEW_LINE +
                "                        .withStatusCode(304)" + NEW_LINE +
                "                        .withReasonPhrase(\"randomPhrase\")" + NEW_LINE +
                "        );",
            new ExpectationToJavaSerializer().serialize(1,
                new Expectation(
                    request()
                        .withPath("somePath")
                        .withBody(new StringBody("responseBody")),
                    once(),
                    unlimited()
                )
                    .thenRespond(
                        response()
                            .withStatusCode(304)
                            .withReasonPhrase("randomPhrase")
                    )
            )
        );
    }
}
