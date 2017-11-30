package org.mockserver.templates.engine.velocity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;

import javax.script.ScriptException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class VelocityTemplateEngineTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

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

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine().executeTemplate(template, request()
                        .withPath("/somePath")
                        .withMethod("POST")
                        .withBody("some_body"),
                HttpResponseDTO.class
        );

        // then
        assertThat(actualHttpResponse, is(
                response()
                        .withStatusCode(200)
                        .withBody("{'name': 'value'}")
        ));
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

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine().executeTemplate(template, request()
                        .withPath("/someOtherPath")
                        .withBody("some_body"),
                HttpResponseDTO.class
        );

        // then
        assertThat(actualHttpResponse, is(
                response()
                        .withStatusCode(406)
                        .withBody("some_body")
        ));
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

        // when
        HttpRequest actualHttpRequest = new VelocityTemplateEngine().executeTemplate(template, request()
                        .withPath("/somePath")
                        .withCookie("someCookie", "someValue")
                        .withMethod("POST")
                        .withBody("some_body"),
                HttpRequestDTO.class
        );

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


        // when
        HttpRequest actualHttpRequest = new VelocityTemplateEngine().executeTemplate(template, request()
                        .withPath("/someOtherPath")
                        .withQueryStringParameter("queryParameter", "someValue")
                        .withBody("some_body"),
                HttpRequestDTO.class
        );

        // then
        assertThat(actualHttpRequest, is(
                request()
                        .withHeader("Host", "localhost:1080")
                        .withPath("/somePath")
                        .withQueryStringParameter("queryParameter", "someValue")
                        .withBody("{'name': 'value'}")
        ));
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
                "\t#if {" + NEW_LINE +
                "\t    'path' : \"/somePath\"," + NEW_LINE +
                "\t    'queryStringParameters' : [ {" + NEW_LINE +
                "\t        'name' : \"queryParameter\"," + NEW_LINE +
                "\t        'values' : [ \"$!request.queryStringParameters['queryParameter'][0]\" ]" + NEW_LINE +
                "\t    } ]," + NEW_LINE +
                "\t    'headers' : [ {" + NEW_LINE +
                "\t        'name' : \"Host\"," + NEW_LINE +
                "\t        'values' : [ \"localhost:1080\" ]" + NEW_LINE +
                "\t    } ]," + NEW_LINE +
                "\t    'body': \"{'name': 'value'}\"" + NEW_LINE +
                "\t}" + NEW_LINE +
                NEW_LINE +
                " for request:" + NEW_LINE +
                NEW_LINE +
                "\t{" + NEW_LINE +
                "\t  \"path\" : \"/someOtherPath\"," + NEW_LINE +
                "\t  \"queryStringParameters\" : [ {" + NEW_LINE +
                "\t    \"name\" : \"queryParameter\"," + NEW_LINE +
                "\t    \"values\" : [ \"someValue\" ]" + NEW_LINE +
                "\t  } ]," + NEW_LINE +
                "\t  \"body\" : \"some_body\"" + NEW_LINE +
                "\t}"));

        // when
        new VelocityTemplateEngine().executeTemplate(template, request()
                        .withPath("/someOtherPath")
                        .withQueryStringParameter("queryParameter", "someValue")
                        .withBody("some_body"),
                HttpRequestDTO.class
        );
    }

}