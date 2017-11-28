package org.mockserver.templates.engine.javascript;

import org.junit.Test;
import org.mockserver.mock.action.HttpResponseTemplateActionHandler;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;

import javax.script.ScriptEngineManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class JavaScriptTemplateEngineTest {

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateFirstExample() {
        // given
        String template = "if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
                "    return {" + NEW_LINE +
                "        'statusCode': 200," + NEW_LINE +
                "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
                "    };" + NEW_LINE +
                "} else {" + NEW_LINE +
                "    return {" + NEW_LINE +
                "        'statusCode': 406," + NEW_LINE +
                "        'body': request.body" + NEW_LINE +
                "    };" + NEW_LINE +
                "}";

        // when
        HttpResponse actualHttpResponse = new JavaScriptTemplateEngine().executeTemplate(template, request()
                .withPath("/somePath")
                .withMethod("POST")
                .withBody("some_body")
        );

        // then
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            assertThat(actualHttpResponse, is(
                    response()
                            .withStatusCode(200)
                            .withBody("{\"name\":\"value\"}")
            ));
        } else {
            assertThat(actualHttpResponse, is(
                    notFoundResponse()
            ));
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateSecondExample() {
        // given
        String template = "if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
                "    return {" + NEW_LINE +
                "        'statusCode': 200," + NEW_LINE +
                "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
                "    };" + NEW_LINE +
                "} else {" + NEW_LINE +
                "    return {" + NEW_LINE +
                "        'statusCode': 406," + NEW_LINE +
                "        'body': request.body" + NEW_LINE +
                "    };" + NEW_LINE +
                "}";

        // when
        HttpResponse actualHttpResponse = new JavaScriptTemplateEngine().executeTemplate(template, request()
                .withPath("/someOtherPath")
                .withBody("some_body")
        );

        // then
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            assertThat(actualHttpResponse, is(
                    response()
                            .withStatusCode(406)
                            .withBody("some_body")
            ));
        } else {
            assertThat(actualHttpResponse, is(
                    notFoundResponse()
            ));
        }
    }

}