package org.mockserver.mock.action;

import org.junit.Test;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateActionHandlerTest {

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateFirstExample() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "if (request.method === 'POST' && request.path === '/somePath') {\n" +
                "    return {\n" +
                "        'statusCode': 200,\n" +
                "        'body': JSON.stringify({name: 'value'})\n" +
                "    };\n" +
                "} else {\n" +
                "    return {\n" +
                "        'statusCode': 406,\n" +
                "        'body': request.body\n" +
                "    };\n" +
                "}");

        // when
        HttpResponse actualHttpResponse = new HttpResponseTemplateActionHandler().handle(template, request()
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
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "if (request.method === 'POST' && request.path === '/somePath') {\n" +
                "    return {\n" +
                "        'statusCode': 200,\n" +
                "        'body': JSON.stringify({name: 'value'})\n" +
                "    };\n" +
                "} else {\n" +
                "    return {\n" +
                "        'statusCode': 406,\n" +
                "        'body': request.body\n" +
                "    };\n" +
                "}");

        // when
        HttpResponse actualHttpResponse = new HttpResponseTemplateActionHandler().handle(template, request()
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

    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateFirstExample() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "#if ( $request.method.value == \"POST\" )\n" +
                "    {\n" +
                "        'statusCode': 200,\n" +
                "        'body': \"{'name': 'value'}\"\n" +
                "    }\n" +
                "#else\n" +
                "    {\n" +
                "        'statusCode': 406,\n" +
                "        'body': $request.body\n" +
                "    }\n" +
                "#end");

        // when
        HttpResponse actualHttpResponse = new HttpResponseTemplateActionHandler().handle(template, request()
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
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "#if ( $request.method.value == \"POST\" )\n" +
                "    {\n" +
                "        'statusCode': 200,\n" +
                "        'body': \"{'name': 'value'}\"\n" +
                "    }\n" +
                "#else\n" +
                "    {\n" +
                "        'statusCode': 406,\n" +
                "        'body': $request.body\n" +
                "    }\n" +
                "#end");

        // when
        HttpResponse actualHttpResponse = new HttpResponseTemplateActionHandler().handle(template, request()
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

    @Test
    public void shouldHandleHttpRequestsWithGroovyTemplateSecondExample() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.GROOVY, "{\n" +
                "    'statusCode': 406,\n" +
                "    'body': '$request.body'\n" +
                "}");

        // when
        HttpResponse actualHttpResponse = new HttpResponseTemplateActionHandler().handle(template, request()
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