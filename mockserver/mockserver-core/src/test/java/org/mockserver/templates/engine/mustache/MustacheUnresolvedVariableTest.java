package org.mockserver.templates.engine.mustache;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.TemplateEvaluationException;
import org.mockserver.templates.engine.TemplateWarning;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * Reporting the variables that did not resolve, for the template dry run, must not change what the template renders.
 */
public class MustacheUnresolvedVariableTest {

    private static final HttpRequest REQUEST = request().withMethod("POST").withPath("/orders/42");

    @Test
    public void rendersExactlyWhatServingRendersWhileReportingTheVariablesThatDidNotResolve() {
        String template = "{\n  \"statusCode\": 200,\n  \"body\": \"[{{reqest.path}}][{{request.pth}}][{{request.path}}]\"\n}";
        List<TemplateWarning> warnings = new ArrayList<>();

        HttpResponse served = new MustacheTemplateEngine(new MockServerLogger(), configuration()).executeTemplate(template, REQUEST, HttpResponseDTO.class);
        HttpResponse dryRun = new MustacheTemplateEngine(new MockServerLogger(), configuration()).executeTemplate(template, REQUEST, HttpResponseDTO.class, warnings::add);

        assertThat(dryRun, is(served));
        assertThat(served.getBodyAsString(), is("[][][/orders/42]"));
        assertThat(warnings, contains(
            new TemplateWarning("{{reqest.path}} did not resolve against this request, so it rendered as empty text", 3, null),
            new TemplateWarning("{{request.pth}} did not resolve against this request, so it rendered as empty text", 3, null)
        ));
    }

    @Test
    public void doesNotRenderATemplateTheSandboxRejected() {
        List<TemplateWarning> warnings = new ArrayList<>();
        MustacheTemplateEngine engine = new MustacheTemplateEngine(new MockServerLogger(), configuration().mustacheDisallowedText("forbidden"));

        assertThrows(TemplateEvaluationException.class, () -> engine.executeTemplate("{{missing}} forbidden", REQUEST, HttpResponseDTO.class, warnings::add));

        assertThat(warnings, is(empty()));
    }
}
