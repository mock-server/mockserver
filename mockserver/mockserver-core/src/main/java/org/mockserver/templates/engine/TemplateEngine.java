package org.mockserver.templates.engine;

import org.mockserver.serialization.model.DTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.Map;

/**
 * @author jamesdbloom
 */
public interface TemplateEngine {

    <T> T executeTemplate(String template, HttpRequest httpRequest, Class<? extends DTO<T>> dtoClass);

    <T> T executeTemplate(String template, HttpRequest httpRequest, HttpResponse httpResponse, Class<? extends DTO<T>> dtoClass);

    /**
     * As {@link #executeTemplate(String, HttpRequest, Class)}, or with {@code httpResponse} as
     * {@link #executeTemplate(String, HttpRequest, HttpResponse, Class)}, also giving the template an
     * {@link org.mockserver.model.HttpTemplate}'s {@code parameters} as {@code parameters}. Null or empty
     * parameters bind nothing, so the render is the same as without them.
     */
    default <T> T executeTemplate(String template, Map<String, Object> parameters, HttpRequest httpRequest, HttpResponse httpResponse, Class<? extends DTO<T>> dtoClass) {
        return httpResponse == null ? executeTemplate(template, httpRequest, dtoClass) : executeTemplate(template, httpRequest, httpResponse, dtoClass);
    }

    /**
     * Renders a template against the request and returns the raw rendered text, without attempting to
     * deserialize it into an {@link org.mockserver.model.HttpResponse} / {@link HttpRequest}. Used to
     * template the contents of a {@link org.mockserver.model.FileBody} so an externally stored response
     * body can contain template placeholders. Supported by the text-based engines (Velocity, Mustache).
     */
    String renderTemplate(String template, HttpRequest httpRequest);

    /**
     * Renders a template against the request and returns the raw rendered text fragment, for use as a
     * streaming payload (an SSE event's {@code data}, a WebSocket text frame, or a gRPC stream message).
     * Unlike {@link #renderTemplate(String, HttpRequest)} — which the text-based engines (Velocity,
     * Mustache) support but the JavaScript engine cannot, because a JavaScript template constructs a
     * whole response object — this method is supported by all three engines: the JavaScript engine
     * executes the template's {@code handle(request)} function and coerces its return value to text
     * (a returned string is used verbatim; any other value is {@code JSON.stringify}'d). The default
     * delegates to {@link #renderTemplate(String, HttpRequest)} so the text-based engines need no
     * override.
     *
     * @param template    the template text
     * @param httpRequest the request context
     * @return the rendered text fragment
     */
    default String renderTemplateText(String template, HttpRequest httpRequest) {
        return renderTemplate(template, httpRequest);
    }

    /**
     * Renders a template against the request with an optional per-iteration load-scenario
     * variable injected under the key {@code "iteration"}, returning the raw rendered text.
     * Used only by the load-generation executor to vary a request step's fields per iteration
     * (e.g. {@code $iteration.index}). When {@code iteration} is {@code null} this is identical
     * to {@link #renderTemplate(String, HttpRequest)}. The default delegates to that method so
     * engines that do not support the iteration variable (JavaScript via this text path) still
     * render the request context unchanged.
     *
     * @param template     the template text
     * @param httpRequest  the request context
     * @param iteration    the per-iteration variable, or {@code null} for no iteration context
     */
    default String renderTemplate(String template, HttpRequest httpRequest, org.mockserver.load.IterationContext iteration) {
        return renderTemplate(template, httpRequest);
    }

}
