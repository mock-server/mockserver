package org.mockserver.mock.action.http;

import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEvaluationException;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateTimeoutException;
import org.mockserver.templates.engine.velocity.VelocityTemplateTimeoutException;

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
        this.detail = abbreviate(templateType + " " + templateRole + " template failed: " + engineMessage(failure), MAX_DETAIL_LENGTH);
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
}
