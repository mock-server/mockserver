package org.mockserver.templates.engine.velocity;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockserver.log.TimeService;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.HttpResponseDTO;

import javax.script.ScriptException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATED;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.INFO;

/**
 * @author jamesdbloom
 */
public class VelocityTemplateEngineTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Mock
    private MockServerLogger logFormatter;

    @BeforeClass
    public static void fixTime() {
        TimeService.fixedTime = true;
    }

    @Before
    public void setupTestFixture() {
        initMocks(this);
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateFirstExample() {
        // given
        String template = "#if ( $request.method == 'POST' && $request.path == '/somePath' )" + NEW_LINE +
            "    {" + NEW_LINE +
            "        'statusCode': 200," + NEW_LINE +
            "        'body': \"{'name': 'value'}\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "#else" + NEW_LINE +
            "    {" + NEW_LINE +
            "        'statusCode': 406," + NEW_LINE +
            "        'body': \"$!request.body\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "#end";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'name': 'value'}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'name': 'value'}\"" + NEW_LINE +
                        "    }" + NEW_LINE,
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateSecondExample() {
        // given
        String template = "#if ( $request.method == 'POST' && $request.path == '/somePath' )" + NEW_LINE +
            "    {" + NEW_LINE +
            "        'statusCode': 200," + NEW_LINE +
            "        'body': \"{'name': 'value'}\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "#else" + NEW_LINE +
            "    {" + NEW_LINE +
            "        'statusCode': 406," + NEW_LINE +
            "        'body': \"$!request.body\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "#end";
        HttpRequest request = request()
            .withPath("/someOtherPath")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(406)
                .withBody("some_body")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 406," + NEW_LINE +
                        "        'body': \"some_body\"" + NEW_LINE +
                        "    }" + NEW_LINE,
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityForwardTemplateFirstExample() {
        // given
        String template = "{" + NEW_LINE +
            "    'path' : \"/somePath\"," + NEW_LINE +
            "    'cookies' : [ {" + NEW_LINE +
            "        'name' : \"$!request.cookies['someCookie']\"," + NEW_LINE +
            "        'value' : \"someCookie\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "        'name' : \"someCookie\"," + NEW_LINE +
            "        'value' : \"$!request.cookies['someCookie']\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'keepAlive' : true," + NEW_LINE +
            "    'secure' : true," + NEW_LINE +
            "    'body' : \"some_body\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withCookie("someCookie", "someValue")
            .withMethod("POST")
            .withBody("some_body");

        // when
        HttpRequest actualHttpRequest = new VelocityTemplateEngine(logFormatter).executeTemplate(template, request, HttpRequestDTO.class);

        // then
        assertThat(actualHttpRequest, is(
            request()
                .withPath("/somePath")
                .withCookie("someCookie", "someValue")
                .withCookie("someValue", "someCookie")
                .withKeepAlive(true)
                .withSecure(true)
                .withBody("some_body")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments("" +
                        "{" + NEW_LINE +
                        "    'path' : \"/somePath\"," + NEW_LINE +
                        "    'cookies' : [ {" + NEW_LINE +
                        "        'name' : \"someValue\"," + NEW_LINE +
                        "        'value' : \"someCookie\"" + NEW_LINE +
                        "    }, {" + NEW_LINE +
                        "        'name' : \"someCookie\"," + NEW_LINE +
                        "        'value' : \"someValue\"" + NEW_LINE +
                        "    } ]," + NEW_LINE +
                        "    'keepAlive' : true," + NEW_LINE +
                        "    'secure' : true," + NEW_LINE +
                        "    'body' : \"some_body\"" + NEW_LINE +
                        "}",
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityForwardTemplateSecondExample() {
        // given
        String template = "{" + NEW_LINE +
            "    'path' : \"/somePath\"," + NEW_LINE +
            "    'queryStringParameters' : [ {" + NEW_LINE +
            "        'name' : \"queryParameter\"," + NEW_LINE +
            "        'values' : [ \"$!request.queryStringParameters['queryParameter'][0]\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'headers' : [ {" + NEW_LINE +
            "        'name' : \"Host\"," + NEW_LINE +
            "        'values' : [ \"localhost:1080\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'body': \"{'name': 'value'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/someOtherPath")
            .withQueryStringParameter("queryParameter", "someValue")
            .withBody("some_body");


        // when
        HttpRequest actualHttpRequest = new VelocityTemplateEngine(logFormatter).executeTemplate(template, request, HttpRequestDTO.class);

        // then
        assertThat(actualHttpRequest, is(
            request()
                .withHeader("Host", "localhost:1080")
                .withPath("/somePath")
                .withQueryStringParameter("queryParameter", "someValue")
                .withBody("{'name': 'value'}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments("" +
                    "{" + NEW_LINE +
                    "    'path' : \"/somePath\"," + NEW_LINE +
                    "    'queryStringParameters' : [ {" + NEW_LINE +
                    "        'name' : \"queryParameter\"," + NEW_LINE +
                    "        'values' : [ \"someValue\" ]" + NEW_LINE +
                    "    } ]," + NEW_LINE +
                    "    'headers' : [ {" + NEW_LINE +
                    "        'name' : \"Host\"," + NEW_LINE +
                    "        'values' : [ \"localhost:1080\" ]" + NEW_LINE +
                    "    } ]," + NEW_LINE +
                    "    'body': \"{'name': 'value'}\"" + NEW_LINE +
                    "}",
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleInvalidVelocityTemplate() {
        // given
        String template = "#if {" + NEW_LINE +
            "    'path' : \"/somePath\"," + NEW_LINE +
            "    'queryStringParameters' : [ {" + NEW_LINE +
            "        'name' : \"queryParameter\"," + NEW_LINE +
            "        'values' : [ \"$!request.queryStringParameters['queryParameter'][0]\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'headers' : [ {" + NEW_LINE +
            "        'name' : \"Host\"," + NEW_LINE +
            "        'values' : [ \"localhost:1080\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'body': \"{'name': 'value'}\"" + NEW_LINE +
            "}";
        exception.expect(RuntimeException.class);
        exception.expectCause(isA(ScriptException.class));
        exception.expectMessage(containsString("Exception transforming template:" + NEW_LINE +
            NEW_LINE +
            "  #if {" + NEW_LINE +
            "      'path' : \"/somePath\"," + NEW_LINE +
            "      'queryStringParameters' : [ {" + NEW_LINE +
            "          'name' : \"queryParameter\"," + NEW_LINE +
            "          'values' : [ \"$!request.queryStringParameters['queryParameter'][0]\" ]" + NEW_LINE +
            "      } ]," + NEW_LINE +
            "      'headers' : [ {" + NEW_LINE +
            "          'name' : \"Host\"," + NEW_LINE +
            "          'values' : [ \"localhost:1080\" ]" + NEW_LINE +
            "      } ]," + NEW_LINE +
            "      'body': \"{'name': 'value'}\"" + NEW_LINE +
            "  }" + NEW_LINE +
            NEW_LINE +
            " for request:" + NEW_LINE +
            NEW_LINE +
            "  {" + NEW_LINE +
            "    \"path\" : \"/someOtherPath\"," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryParameter\" : [ \"someValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"some_body\"" + NEW_LINE +
            "  }"));

        // when
        new VelocityTemplateEngine(logFormatter).executeTemplate(template, request()
                .withPath("/someOtherPath")
                .withQueryStringParameter("queryParameter", "someValue")
                .withBody("some_body"),
            HttpRequestDTO.class
        );
    }

}
