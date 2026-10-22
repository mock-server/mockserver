package org.mockserver.extension;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * An extension that serves its own control-plane routes under {@code /mockserver/extensions/<name>/}, where
 * {@code <name>} is its {@link #name()}. The routes take the same control-plane origin check, authentication,
 * authorization, audit and CORS headers as MockServer's own routes, so a caller the control plane would refuse never
 * reaches {@link #handle}.
 * <p>
 * On the Netty server each call runs on a pool of its own, never on a network thread or MockServer's shared
 * scheduler. At most {@code mockserver.extensionAdminMaxInFlight} calls run at once, and a call beyond that is
 * answered {@code 503}. A call that has not answered within {@code mockserver.extensionAdminTimeoutMillis} is answered
 * {@code 500} and, if it is still in this extension's code, its thread is interrupted; whatever it returns afterwards
 * is discarded. In a WAR the call runs on
 * the servlet container's request thread, with the same limit but no timeout.
 * <p>
 * Only the names of loaded extensions are taken: any other path under {@code /mockserver/extensions/} is handled as
 * it would be with no extension loaded, and an expectation for a path under a loaded extension's name no longer
 * matches (start-up logs a warning when an expectation loaded at start-up is affected).
 */
public interface AdminApiExtension extends Extension {

    /**
     * Answers one request to this extension's routes.
     *
     * @param request the request, as received
     * @param subPath the request path after {@code /mockserver/extensions/<name>}: {@code /} when nothing follows the
     *                name, otherwise starting with {@code /}
     * @return the response; {@code null} is answered {@code 404}
     * @throws Exception which is answered {@code 500} with a correlation id, logged with the exception
     */
    HttpResponse handle(HttpRequest request, String subPath) throws Exception;

    /**
     * Whether a route only reads state, so that, with {@code controlPlaneAuthorizationEnabled}, a principal granted
     * only the read role may call it. Every route needs the role that may change state unless this returns true,
     * whatever its method, {@code GET} included. Called at most once per call, on the same thread as {@link #handle}
     * and before it, only after the caller has been authenticated and only when authorization or the audit needs the
     * answer, so an unauthenticated caller never runs it. A throw is answered {@code 500}, like a throw from
     * {@link #handle}.
     *
     * @param method  the request method
     * @param subPath as for {@link #handle}
     * @return true when the route only reads state
     */
    default boolean isReadOnly(String method, String subPath) {
        return false;
    }
}
