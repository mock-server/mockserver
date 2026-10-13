package org.mockserver.templates.engine;

/**
 * Something a template did that is probably not what its author meant but did not fail it, such as a
 * Velocity reference that did not resolve, with its 1-based template line and column when known.
 */
public record TemplateWarning(String message, Integer line, Integer column) {
}
