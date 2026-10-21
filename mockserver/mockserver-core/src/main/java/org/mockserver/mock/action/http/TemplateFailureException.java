package org.mockserver.mock.action.http;

import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEvaluationException;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateTimeoutException;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;
import org.mockserver.templates.engine.velocity.VelocityTemplateTimeoutException;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * A response or forward template that failed to render. It keeps the engine's failure as its cause and
 * message, so the failure is logged as before, and lets the action handler answer the client as a
 * template failure rather than as a request that matched no expectation.
 */
class TemplateFailureException extends RuntimeException {

    static final int MAX_DETAIL_LENGTH = 8192;

    private final String detail;

    TemplateFailureException(HttpTemplate.TemplateType templateType, String templateRole, RuntimeException failure) {
        super(failure.getMessage(), failure);
        this.detail = abbreviate(describe(templateType, templateRole) + " template failed: " + engineMessage(failure), MAX_DETAIL_LENGTH);
    }

    /**
     * A template that rendered, but whose output is not a valid request or response.
     */
    private TemplateFailureException(HttpTemplate.TemplateType templateType, String templateRole, String rendersA, String validationErrors) {
        super(abbreviate(describe(templateType, templateRole) + " template rendered output that is not a valid " + rendersA
            + (isNotBlank(validationErrors) ? ": " + validationErrors : ""), MAX_DETAIL_LENGTH));
        this.detail = getMessage();
    }

    /**
     * Renders a template with {@code render}, failing with this exception if the engine throws or, when
     * {@code invalidOutputIsFailure}, if the output is not a valid {@code rendersA} (otherwise rendered as null).
     */
    static <T> T render(HttpTemplate httpTemplate, String templateRole, String rendersA, boolean invalidOutputIsFailure, Supplier<T> render) {
        AtomicReference<String> validationErrors = new AtomicReference<>();
        T rendered;
        try {
            rendered = invalidOutputIsFailure
                ? HttpTemplateOutputDeserializer.reportingInvalidOutput(reason -> validationErrors.compareAndSet(null, reason), render)
                : render.get();
        } catch (RuntimeException failure) {
            throw new TemplateFailureException(httpTemplate.getTemplateType(), templateRole, failure);
        }
        if (rendered == null && invalidOutputIsFailure) {
            throw new TemplateFailureException(httpTemplate.getTemplateType(), templateRole, rendersA, validationErrors.get());
        }
        return rendered;
    }

    private static String describe(HttpTemplate.TemplateType templateType, String templateRole) {
        return templateType != null ? templateType + " " + templateRole : templateRole;
    }

    /**
     * @return the template failure in {@code failure}'s cause chain, or null
     */
    static TemplateFailureException in(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause instanceof TemplateFailureException templateFailure) {
                return templateFailure;
            }
        }
        return null;
    }

    /**
     * The engine's own message, without the template text and request that the transform-failure wrapper adds,
     * followed by the template line, column and hint when the engine reports them.
     */
    private static String engineMessage(RuntimeException failure) {
        Throwable reported = failure;
        if (!(failure instanceof JavaScriptTemplateTimeoutException) && !(failure instanceof VelocityTemplateTimeoutException) && failure.getCause() != null) {
            reported = failure.getCause();
        }
        StringBuilder message = new StringBuilder(isNotBlank(reported.getMessage()) ? reported.getMessage() : reported.getClass().getSimpleName());
        if (failure instanceof TemplateEvaluationException located) {
            if (located.getLine() != null) {
                message.append('\n').append("at template line ").append(located.getLine());
                if (located.getColumn() != null) {
                    message.append(", column ").append(located.getColumn());
                }
            }
            if (isNotBlank(located.getHint())) {
                message.append('\n').append("hint: ").append(located.getHint());
            }
        }
        return message.toString();
    }

    /**
     * @return a description of the failure for the client, at most {@link #MAX_DETAIL_LENGTH} characters
     */
    String getDetail() {
        return detail;
    }

    /**
     * @return the engine's failure to log, or this exception when the template rendered output that is not valid
     */
    Throwable loggedFailure() {
        return getCause() != null ? getCause() : this;
    }
}
