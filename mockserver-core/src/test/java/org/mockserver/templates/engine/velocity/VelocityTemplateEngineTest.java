package org.mockserver.templates.engine.velocity;

import org.junit.Test;
import org.mockserver.mock.action.HttpResponseTemplateActionHandler;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class VelocityTemplateEngineTest {


    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateFirstExample() {
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
                .withBody("some_body")
        );

        // then
        assertThat(actualHttpResponse, is(
                response()
                        .withStatusCode(200)
                        .withBody("{'name': 'value'}")
        ));
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateSecondExample() {
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
                .withBody("some_body")
        );

        // then
        assertThat(actualHttpResponse, is(
                response()
                        .withStatusCode(406)
                        .withBody("some_body")
        ));
    }

}