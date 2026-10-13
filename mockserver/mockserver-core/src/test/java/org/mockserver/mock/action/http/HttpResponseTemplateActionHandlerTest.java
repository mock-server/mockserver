package org.mockserver.mock.action.http;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.templates.engine.javascript.JavaScriptTemplateEngineTest.graalJsAvailable;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateActionHandlerTest {

    private HttpResponseTemplateActionHandler httpResponseTemplateActionHandler;

    @Before
    public void setupMocks() {
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        // javascriptTemplateExecutionTimeout(0L) disables the JS template watchdog (see PolyglotRunner):
        // these tests exercise normal JS template execution, not the timeout feature, so they must not
        // depend on wall-clock time. Under parallel CI load GraalJS runs interpreter-only and a first
        // execute can exceed the production default, making this flaky. Production default is unchanged.
        httpResponseTemplateActionHandler = new HttpResponseTemplateActionHandler(mockLogFormatter, new Configuration().javascriptTemplateExecutionTimeout(0L));
        openMocks(this);
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateFirstExample() {
        // given
        graalJsAvailable();
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
        if (JavaScriptTemplateEngine.isPolyglotAvailable()) {
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
        graalJsAvailable();
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
        if (JavaScriptTemplateEngine.isPolyglotAvailable()) {
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
    public void shouldHandleHttpRequestsWithVelocityTemplateLoadedFromFile() {
        // given - template provided via withTemplateFile instead of inline withTemplate
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY)
            .withTemplateFile("org/mockserver/templates/sample_velocity_response.vm");

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
    public void shouldPreferInlineTemplateOverTemplateFile() {
        // given - both inline template and templateFile set; inline wins
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "#if ( $request.method == 'POST' && $request.path == '/somePath' )" + NEW_LINE +
            "    {" + NEW_LINE +
            "        'statusCode': 201," + NEW_LINE +
            "        'body': \"{'from': 'inline'}\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "#end")
            .withTemplateFile("org/mockserver/templates/sample_velocity_response.vm");

        // when
        HttpResponse actualHttpResponse = httpResponseTemplateActionHandler.handle(template, request()
            .withPath("/somePath")
            .withMethod("POST")
            .withBody("some_body")
        );

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(201)
                .withBody("{'from': 'inline'}")
        ));
    }

    @Test
    public void shouldFallBackToNotFoundWhenVelocityTemplateRendersInvalidResponse() {
        // given - a template that renders valid JSON which is NOT a valid HttpResponse ("path" is a
        // request-only field), so response schema validation fails and no response can be produced
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY,
            "{ 'path': \"$!request.path\" }");

        // when
        HttpResponse actualHttpResponse = httpResponseTemplateActionHandler.handle(template, request()
            .withPath("/api/orders/42")
            .withMethod("GET")
        );

        // then - the client response semantics are unchanged: an invalid rendered template degrades to 404.
        // The failure itself is surfaced as a TEMPLATE_GENERATION_FAILED log event (asserted in
        // MustacheTemplateEngineTest at the shared HttpTemplateOutputDeserializer layer).
        assertThat(actualHttpResponse, is(notFoundResponse()));
    }

    @Test
    public void shouldReportTemplateThatThrowsAsTemplateFailure() {
        // given - a template whose parse fails
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "#foreach(");

        // when
        TemplateFailureException failure = assertThrows(TemplateFailureException.class, () -> httpResponseTemplateActionHandler.handle(template, request().withPath("/somePath")));

        // then - the message is still the engine's transform failure, and the detail drops the template and request
        assertThat(failure.getMessage(), startsWith("Exception:"));
        assertThat(failure.getMessage(), containsString("transforming template:"));
        assertThat(failure.getDetail(), startsWith("VELOCITY response template failed: "));
        assertThat(failure.getDetail(), not(containsString("transforming template")));
        assertThat(failure.getDetail(), not(containsString("/somePath")));
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
