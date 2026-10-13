package org.mockserver.templates.engine;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * A failing template reports the template line and column of the failure, in the exception and, where the
 * engine's own message does not give it in template coordinates, in the "Exception: ... transforming
 * template" message the failure event carries.
 */
public class TemplateErrorLocationTest {

    private static final MockServerLogger LOGGER = new MockServerLogger(TemplateErrorLocationTest.class);
    private static final HttpRequest REQUEST = request().withMethod("GET").withPath("/orders");

    private static TemplateEvaluationException failure(TemplateEngine engine, String template) {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> engine.executeTemplate(template, REQUEST, HttpResponseDTO.class));
        assertThat(failure, instanceOf(TemplateEvaluationException.class));
        assertThat(failure.getMessage(), startsWith("Exception:"));
        assertThat(failure.getMessage(), containsString(" transforming template:"));
        return (TemplateEvaluationException) failure;
    }

    @Test
    public void velocityParseErrorCarriesTheTemplateLineAndColumn() {
        TemplateEvaluationException failure = failure(new VelocityTemplateEngine(LOGGER, configuration()),
            "{\n  \"statusCode\": 200,\n  #if {\n}");

        assertThat(failure.getLine(), is(3));
        assertThat(failure.getColumn(), is(7));
        assertThat(failure.getMessage(), containsString("[line 3, column 7]"));
    }

    @Test
    public void velocityMethodFailureCarriesTheTemplateLineAndColumn() {
        TemplateEvaluationException failure = failure(new VelocityTemplateEngine(LOGGER, configuration()),
            "{\n  \"statusCode\": 200,\n  \"body\": \"$regex.matches('x', '[')\"\n}");

        assertThat(failure.getLine(), is(3));
        // the column of the method name, where Velocity reports the call
        assertThat(failure.getColumn(), is(19));
    }

    @Test
    public void velocityReportsUnresolvedReferencesWithTheirPositionWhenAsked() {
        List<TemplateWarning> warnings = new ArrayList<>();

        new VelocityTemplateEngine(LOGGER, configuration()).executeTemplate(
            "{\n  \"statusCode\": 200,\n  \"body\": \"$request.missing $!request.alsoMissing\"\n}",
            REQUEST, HttpResponseDTO.class, warnings::add);

        assertThat(warnings.size(), is(1));
        assertThat(warnings.get(0).message(), startsWith("$request.missing did not resolve"));
        assertThat(warnings.get(0).line(), is(3));
        assertThat(warnings.get(0).column(), is(12));
    }

    @Test
    public void mustacheParseErrorCarriesTheTemplateLine() {
        TemplateEvaluationException failure = failure(new MustacheTemplateEngine(LOGGER, configuration()),
            "{\n  \"statusCode\": 200,\n  {{#request.path}}\n  {{/request.method}}\n}");

        assertThat(failure.getLine(), is(4));
        assertThat(failure.getColumn(), nullValue());
        assertThat(failure.getMessage(), containsString("@ line 4"));
    }

    @Test
    public void javaScriptSyntaxErrorIsReportedInTemplateCoordinates() {
        requireGraalJs();
        TemplateEvaluationException failure = failure(new JavaScriptTemplateEngine(LOGGER, configuration()),
            "var status = 200;\nreturn { statusCode: status,, body: 'x' };");

        assertThat(failure.getLine(), is(2));
        assertThat(failure.getColumn(), is(29));
        assertThat(failure.getMessage(), containsString("at template line 2, column 29"));
    }

    @Test
    public void javaScriptUndefinedVariableNamesTheVariableWithItsPosition() {
        requireGraalJs();
        TemplateEvaluationException failure = failure(new JavaScriptTemplateEngine(LOGGER, configuration()),
            "var status = 200;\n\nreturn { statusCode: status, body: requestBody };");

        assertThat(failure.getLine(), is(3));
        assertThat(failure.getColumn(), is(36));
        assertThat(failure.getHint(), startsWith("requestBody is not defined"));
        assertThat(failure.getMessage(), containsString("hint: requestBody is not defined"));
    }

    @Test
    public void javaScriptReadOfAMissingFieldGetsAHint() {
        requireGraalJs();
        TemplateEvaluationException failure = failure(new JavaScriptTemplateEngine(LOGGER, configuration()),
            "return { statusCode: 200, body: request.headers['x-missing'][0].length };");

        assertThat(failure.getLine(), is(1));
        assertThat(failure.getHint(), containsString("read a field of undefined or null"));
    }

    @Test
    public void javaScriptUnclosedBraceIsReportedAfterTheTemplate() {
        requireGraalJs();
        TemplateEvaluationException failure = failure(new JavaScriptTemplateEngine(LOGGER, configuration()),
            "if (request.method === 'GET') {\nreturn { statusCode: 200 };");

        assertThat(failure.getLine(), nullValue());
        assertThat(failure.getHint(), containsString("probably not closed"));
        assertThat(failure.getMessage(), not(containsString("at template line")));
    }

    private static void requireGraalJs() {
        assertThat("GraalJS is on mockserver-core's test classpath", JavaScriptTemplateEngine.isPolyglotAvailable(), is(true));
    }
}
