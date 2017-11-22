package org.mockserver.mock.action;

import org.junit.Test;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateActionHandlerTest {

    @Test
    public void shouldHandleHttpRequestsFirstExample() {
        // given
        HttpTemplate template = template("if (request.method === 'POST' && request.path === '/somePath') {\n" +
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
        assertThat(actualHttpResponse, is(
                response()
                        .withStatusCode(200)
                        .withBody("{\"name\":\"value\"}")
        ));
    }
    @Test
    public void shouldHandleHttpRequestsSecondExample() {
        // given
        HttpTemplate template = template("if (request.method === 'POST' && request.path === '/somePath') {\n" +
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
        assertThat(actualHttpResponse, is(
                response()
                        .withStatusCode(406)
                        .withBody("some_body")
        ));
    }

}