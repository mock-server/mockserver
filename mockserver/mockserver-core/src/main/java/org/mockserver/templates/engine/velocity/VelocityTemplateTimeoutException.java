package org.mockserver.templates.engine.velocity;

/**
 * Thrown when a Velocity template is stopped because it was still running after the configured
 * {@code mockserver.velocityTemplateExecutionTimeout}; see {@link RenderDeadline} for where it is checked.
 */
public class VelocityTemplateTimeoutException extends RuntimeException {

    public VelocityTemplateTimeoutException(long timeoutMillis) {
        super("Velocity template execution exceeded the configured timeout of " + timeoutMillis + "ms and was stopped; "
            + "increase mockserver.velocityTemplateExecutionTimeout or set it to 0 to disable the timeout");
    }

    VelocityTemplateTimeoutException(String message) {
        super(message);
    }
}
