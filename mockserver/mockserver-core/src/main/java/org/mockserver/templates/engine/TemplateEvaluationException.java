package org.mockserver.templates.engine;

import org.mockserver.model.HttpRequest;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;

/**
 * A template that failed to parse or render, with the template line and column of the failure when the
 * engine reports one, and a hint when the cause is a common mistake. Both are null when unknown.
 */
public class TemplateEvaluationException extends RuntimeException {

    private final Integer line;
    private final Integer column;
    private final String hint;

    private TemplateEvaluationException(String message, Throwable cause, Integer line, Integer column, String hint) {
        super(message, cause);
        this.line = line;
        this.column = column;
        this.hint = hint;
    }

    /**
     * Builds the "Exception: ... transforming template: ... for request: ..." failure. When
     * {@code appendLocation} is true the location is added after the engine's own message, for an engine
     * whose message does not already give it in template coordinates.
     */
    public static TemplateEvaluationException transformFailure(Exception cause, String template, HttpRequest request, Integer line, Integer column, String hint, boolean appendLocation) {
        StringBuilder detail = new StringBuilder(isNotBlank(cause.getMessage()) ? cause.getMessage() : cause.getClass().getSimpleName());
        if (appendLocation && line != null) {
            detail.append(NEW_LINE).append("at template line ").append(line);
            if (column != null) {
                detail.append(", column ").append(column);
            }
        }
        if (isNotBlank(hint)) {
            detail.append(NEW_LINE).append("hint: ").append(hint);
        }
        return new TemplateEvaluationException(
            formatLogMessage("Exception:{}transforming template:{}for request:{}", detail.toString(), template, request),
            cause, line, column, hint
        );
    }

    /**
     * @return the 1-based template line of the failure, or null when the engine does not report one
     */
    public Integer getLine() {
        return line;
    }

    /**
     * @return the 1-based template column of the failure, or null when the engine does not report one
     */
    public Integer getColumn() {
        return column;
    }

    /**
     * @return a suggested fix for a common mistake, or null
     */
    public String getHint() {
        return hint;
    }
}
