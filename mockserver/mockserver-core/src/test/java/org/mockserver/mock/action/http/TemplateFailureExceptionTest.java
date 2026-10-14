package org.mockserver.mock.action.http;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpTemplate;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.TemplateEvaluationException;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * The text body of the 500 that answers a failed template when templateFailureAsServerError is set.
 */
public class TemplateFailureExceptionTest {

    private final HttpRequest request = request().withPath("/somePath");

    @Test
    public void shouldGiveTemplateLineColumnAndHint() {
        TemplateEvaluationException failure = TemplateEvaluationException.transformFailure(
            new IllegalStateException("ReferenceError: total is not defined"), "return { body: total };", request, 2, 17, "declare total before using it", true);

        String detail = new TemplateFailureException(HttpTemplate.TemplateType.JAVASCRIPT, "response", failure).getDetail();

        assertThat(detail, is("JAVASCRIPT response template failed: ReferenceError: total is not defined\n"
            + "at template line 2, column 17\n"
            + "hint: declare total before using it"));
    }

    @Test
    public void shouldGiveTemplateLineWhenEngineReportsNoColumn() {
        TemplateEvaluationException failure = TemplateEvaluationException.transformFailure(
            new IllegalStateException("Improperly closed variable"), "{{#items}", request, 4, null, null, false);

        String detail = new TemplateFailureException(HttpTemplate.TemplateType.MUSTACHE, "forward", failure).getDetail();

        assertThat(detail, is("MUSTACHE forward template failed: Improperly closed variable\nat template line 4"));
    }

    @Test
    public void shouldGiveOnlyEngineMessageWhenEngineReportsNoLocation() {
        TemplateEvaluationException failure = TemplateEvaluationException.transformFailure(
            new IllegalStateException("TEST_EXCEPTION"), "some_template", request, null, null, null, false);

        String detail = new TemplateFailureException(HttpTemplate.TemplateType.VELOCITY, "response", failure).getDetail();

        assertThat(detail, is("VELOCITY response template failed: TEST_EXCEPTION"));
    }

    @Test
    public void shouldGiveLocationOfVelocityParseError() {
        RuntimeException failure = velocityFailure("{ \"statusCode\": 200 }\n#if($request.path == '/x')");

        String detail = new TemplateFailureException(HttpTemplate.TemplateType.VELOCITY, "response", failure).getDetail();

        assertThat(detail, containsString("\nat template line 2, column "));
        assertThat(detail, not(containsString("transforming template")));
    }

    @Test
    public void shouldKeepDetailWithinMaximumLength() {
        TemplateEvaluationException failure = TemplateEvaluationException.transformFailure(
            new IllegalStateException("x".repeat(20_000)), "some_template", request, 1, 1, "y".repeat(20_000), true);

        String detail = new TemplateFailureException(HttpTemplate.TemplateType.JAVASCRIPT, "response", failure).getDetail();

        assertThat(detail.length(), is(TemplateFailureException.MAX_DETAIL_LENGTH));
    }

    private RuntimeException velocityFailure(String template) {
        try {
            new VelocityTemplateEngine(new MockServerLogger(), configuration()).executeTemplate(template, request, HttpResponseDTO.class);
        } catch (RuntimeException failure) {
            return failure;
        }
        fail("the template rendered without failing");
        return null;
    }
}
