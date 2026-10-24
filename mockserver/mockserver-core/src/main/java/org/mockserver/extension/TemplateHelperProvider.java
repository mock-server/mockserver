package org.mockserver.extension;

import java.util.Map;

/**
 * An {@link Extension} that adds helpers to response and forward templates, under {@code ext.<extension name>}:
 * <ul>
 *     <li>Velocity: {@code $ext.acme.Strings.upper($request.path)}</li>
 *     <li>JavaScript: {@code ext.acme.Strings.upper(request.path)}</li>
 *     <li>Mustache: {@code {{#ext.acme.upper}}{{request.path}}{{/ext.acme.upper}}}</li>
 * </ul>
 * Helpers can be called only from an {@code httpResponseTemplate} or {@code httpForwardTemplate}; any other
 * template (a webhook, a secondary action, a delay, a file body, a stream) that calls one fails with an error.
 * <p>
 * Each call runs on a helper thread, never on the thread rendering the template, and the render waits for it for at
 * most {@code mockserver.extensionHelperTimeoutMillis}; that is also the budget for every helper call one render
 * makes, so a template that calls a slow helper in a loop still fails at the timeout. A timed-out call is
 * interrupted and the template fails. At most {@code mockserver.extensionHelperMaxInFlight} calls run at once; a
 * call beyond that fails at once. A helper cannot call another helper. Keep helpers short and non-blocking.
 * <p>
 * Anyone who can create an expectation can call a helper. A helper that returns an object exposing reflection or
 * class loading defeats {@code mockserver.velocityDisallowClassLoading} and the JavaScript class filter, so return
 * strings, numbers, booleans or plain data.
 */
public interface TemplateHelperProvider extends Extension {

    /**
     * Helpers for Velocity and JavaScript templates. Each key must be a public interface and each value an
     * implementation of it. Each is exposed as {@code ext.<extension name>.<interface simple name>}, through a proxy
     * of the interface, so only the interface's methods can be called. Called once, at start-up.
     *
     * @return each helper interface with its implementation
     */
    default Map<Class<?>, Object> helpers() {
        return Map.of();
    }

    /**
     * Helpers for Mustache templates, each used as a section, {@code {{#ext.<extension name>.<key>}}...{{/...}}}.
     * The section is rendered first, as part of the template; the helper receives the rendered text and its result
     * replaces the section. Keys are letters, digits, {@code _} or {@code -}. Called once, at start-up.
     *
     * @return each helper by its key
     */
    default Map<String, MustacheHelper> mustacheHelpers() {
        return Map.of();
    }
}
