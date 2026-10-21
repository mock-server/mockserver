package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpOverrideForwardedRequest;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpResponseModifier;
import org.mockserver.model.HttpTemplate;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequestActionHandler extends HttpForwardAction {

    private volatile VelocityTemplateEngine velocityTemplateEngine;
    private volatile JavaScriptTemplateEngine javascriptTemplateEngine;
    private volatile MustacheTemplateEngine mustacheTemplateEngine;

    public HttpOverrideForwardedRequestActionHandler(MockServerLogger logFormatter, Configuration configuration, NettyHttpClient httpClient) {
        super(logFormatter, configuration, httpClient);
    }

    public HttpForwardActionResult handle(final HttpOverrideForwardedRequest httpOverrideForwardedRequest, final HttpRequest request) {
        if (httpOverrideForwardedRequest != null) {
            HttpRequest requestToSend = request.clone().update(httpOverrideForwardedRequest.getRequestOverride(), httpOverrideForwardedRequest.getRequestModifier());
            boolean hasExplicitHostOverride = httpOverrideForwardedRequest.getRequestOverride() != null
                && httpOverrideForwardedRequest.getRequestOverride().containsHeader("Host");
            if (!hasExplicitHostOverride) {
                adjustHostHeader(requestToSend);
            }
            // Disable streaming only when a response modification needs the fully-buffered body.
            // A HEADER-ONLY modification (status / headers / cookies, with no body change) can be
            // applied to the streaming response HEAD while the body chunks are relayed untouched,
            // so it must NOT force aggregation — otherwise a header-only rewrite (e.g. adding a
            // CORS or trace header) on an SSE / LLM upstream would silently break streaming. Only a
            // body-affecting modification (a body/schema response override, a JSON body patch, or a
            // response template) requires the full response and disables streaming.
            boolean hasResponseModification = httpOverrideForwardedRequest.getResponseOverride() != null
                || httpOverrideForwardedRequest.getResponseModifier() != null
                || httpOverrideForwardedRequest.getResponseTemplate() != null;
            boolean disableStreaming = hasResponseModification
                && !isHeaderOnlyResponseModification(httpOverrideForwardedRequest);
            HttpTemplate responseTemplate = httpOverrideForwardedRequest.getResponseTemplate();
            return sendRequest(request, requestToSend, null, httpResponse -> {
                HttpResponse result = httpResponse;
                if (result == null) {
                    result = httpOverrideForwardedRequest.getResponseOverride();
                } else {
                    result = result.update(httpOverrideForwardedRequest.getResponseOverride(), httpOverrideForwardedRequest.getResponseModifier(), request);
                }
                if (responseTemplate != null && result != null) {
                    TemplateEngine templateEngine = resolveTemplateEngine(responseTemplate);
                    if (templateEngine != null) {
                        HttpResponse responseToTemplate = result;
                        // only with templateFailureAsServerError (the default); when it is false a failure fails the forward
                        HttpResponse templatedResponse = templateFailureAsServerError()
                            ? TemplateFailureException.render(responseTemplate, "forward response", "response", true,
                                () -> templateEngine.executeTemplate(responseTemplate.getTemplateContent(), responseTemplate.getParameters(), request, responseToTemplate, HttpResponseDTO.class))
                            : templateEngine.executeTemplate(responseTemplate.getTemplateContent(), responseTemplate.getParameters(), request, result, HttpResponseDTO.class);
                        if (templatedResponse != null) {
                            result = templatedResponse;
                        }
                    }
                }
                return result;
            }, disableStreaming);
        } else {
            return sendRequest(request, request, null, httpResponse -> httpResponse);
        }
    }

    /**
     * Whether the response modification can be applied to a streaming response's HEAD alone,
     * leaving the body chunks to be relayed untouched. When true, streaming is preserved for an
     * SSE / content-type-less streaming upstream; when false the full response must be buffered.
     * <p>
     * A modification is header-only when:
     * <ul>
     *   <li>there is no {@code responseTemplate} — a template always renders against the full body;</li>
     *   <li>any {@code responseOverride} supplies no body and no generate-from-schema body (only
     *       status / reason / headers / cookies / trailers / connection options / stream id, all of
     *       which live on the head — see {@link HttpResponse#update});</li>
     *   <li>any {@code responseModifier} (and every modifier in its chain) applies no JSON body
     *       patch or merge patch (header / cookie / status-condition edits are head-only).</li>
     * </ul>
     */
    static boolean isHeaderOnlyResponseModification(HttpOverrideForwardedRequest httpOverrideForwardedRequest) {
        if (httpOverrideForwardedRequest.getResponseTemplate() != null) {
            return false;
        }
        HttpResponse responseOverride = httpOverrideForwardedRequest.getResponseOverride();
        if (responseOverride != null
            && (responseOverride.getBody() != null || responseOverride.getGenerateFromSchema() != null)) {
            return false;
        }
        return isHeaderOnlyModifier(httpOverrideForwardedRequest.getResponseModifier());
    }

    private static boolean isHeaderOnlyModifier(HttpResponseModifier modifier) {
        if (modifier == null) {
            return true;
        }
        // When a chain is present, HttpResponseModifier.applyTo() applies only the chain and ignores
        // this modifier's OWN headers/cookies AND its own jsonPatch/jsonMergePatch — so the chain is
        // the unit of work. Mirror that here: inspect only the children, not this modifier's own
        // (unreachable) body patch, otherwise a chain-plus-own-patch modifier would needlessly
        // disable streaming for a patch that never runs.
        if (modifier.getModifiers() != null && !modifier.getModifiers().isEmpty()) {
            for (HttpResponseModifier child : modifier.getModifiers()) {
                if (!isHeaderOnlyModifier(child)) {
                    return false;
                }
            }
            return true;
        }
        if (modifier.getJsonPatch() != null || modifier.getJsonMergePatch() != null) {
            return false;
        }
        return true;
    }

    private TemplateEngine resolveTemplateEngine(HttpTemplate httpTemplate) {
        switch (httpTemplate.getTemplateType()) {
            case VELOCITY:
                return getVelocityTemplateEngine();
            case JAVASCRIPT:
                return getJavaScriptTemplateEngine();
            case MUSTACHE:
                return getMustacheTemplateEngine();
            default:
                throw new RuntimeException("Unknown no template engine available for " + httpTemplate.getTemplateType());
        }
    }

    private VelocityTemplateEngine getVelocityTemplateEngine() {
        VelocityTemplateEngine engine = velocityTemplateEngine;
        if (engine == null) {
            synchronized (this) {
                engine = velocityTemplateEngine;
                if (engine == null) {
                    engine = velocityTemplateEngine = new VelocityTemplateEngine(mockServerLogger, configuration);
                }
            }
        }
        return engine;
    }

    private JavaScriptTemplateEngine getJavaScriptTemplateEngine() {
        JavaScriptTemplateEngine engine = javascriptTemplateEngine;
        if (engine == null) {
            synchronized (this) {
                engine = javascriptTemplateEngine;
                if (engine == null) {
                    engine = javascriptTemplateEngine = new JavaScriptTemplateEngine(mockServerLogger, configuration);
                }
            }
        }
        return engine;
    }

    private MustacheTemplateEngine getMustacheTemplateEngine() {
        MustacheTemplateEngine engine = mustacheTemplateEngine;
        if (engine == null) {
            synchronized (this) {
                engine = mustacheTemplateEngine;
                if (engine == null) {
                    engine = mustacheTemplateEngine = new MustacheTemplateEngine(mockServerLogger, configuration);
                }
            }
        }
        return engine;
    }

    @Override
    protected String actionName() {
        return "override forwarded request action";
    }
}
