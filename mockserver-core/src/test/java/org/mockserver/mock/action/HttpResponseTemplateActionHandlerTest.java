package org.mockserver.mock.action;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;

import javax.script.ScriptEngineManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateActionHandlerTest {

    private HttpResponseTemplateActionHandler httpResponseTemplateActionHandler;

    private MockServerLogger mockLogFormatter;

    @Before
    public void setupMocks() {
        mockLogFormatter = mock(MockServerLogger.class);
        httpResponseTemplateActionHandler = new HttpResponseTemplateActionHandler(mockLogFormatter);
        initMocks(this);
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateFirstExample() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
                "    return {" + NEW_LINE +
                "        'statusCode': 200," + NEW_LINE +
                "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
                "    };" + NEW_LINE +
                "} else {" + NEW_LINE +
                "    return {" + NEW_LINE +
                "        'statusCode': 406," + NEW_LINE +
                "        'body': request.body" + NEW_LINE +
                "    };" + NEW_LINE +
                "}");

        // when
        HttpResponse actualHttpResponse = httpResponseTemplateActionHandler.handle(template, request()
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
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
                "    return {" + NEW_LINE +
                "        'statusCode': 200," + NEW_LINE +
                "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
                "    };" + NEW_LINE +
                "} else {" + NEW_LINE +
                "    return {" + NEW_LINE +
                "        'statusCode': 406," + NEW_LINE +
                "        'body': request.body" + NEW_LINE +
                "    };" + NEW_LINE +
                "}");

        // when
        HttpResponse actualHttpResponse = httpResponseTemplateActionHandler.handle(template, request()
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
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "#if ( $request.method == 'POST' && $request.path == '/somePath' )" + NEW_LINE +
                "    {" + NEW_LINE +
                "        'statusCode': 200," + NEW_LINE +
                "        'body': \"{'name': 'value'}\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "#else" + NEW_LINE +
                "    {" + NEW_LINE +
                "        'statusCode': 406," + NEW_LINE +
                "        'body': \"$!request.body\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "#end");

        // when
        HttpResponse actualHttpResponse = httpResponseTemplateActionHandler.handle(template, request()
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
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "#if ( $request.method == 'POST' && $request.path == '/somePath' )" + NEW_LINE +
                "    {" + NEW_LINE +
                "        'statusCode': 200," + NEW_LINE +
                "        'body': \"{'name': 'value'}\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "#else" + NEW_LINE +
                "    {" + NEW_LINE +
                "        'statusCode': 406," + NEW_LINE +
                "        'body': \"$!request.body\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "#end");

        // when
        HttpResponse actualHttpResponse = httpResponseTemplateActionHandler.handle(template, request()
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
