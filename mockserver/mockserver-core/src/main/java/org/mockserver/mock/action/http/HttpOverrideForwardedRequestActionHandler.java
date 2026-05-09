package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpOverrideForwardedRequest;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
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

    private VelocityTemplateEngine velocityTemplateEngine;
    private JavaScriptTemplateEngine javascriptTemplateEngine;
    private MustacheTemplateEngine mustacheTemplateEngine;

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
            HttpTemplate responseTemplate = httpOverrideForwardedRequest.getResponseTemplate();
            return sendRequest(requestToSend, null, httpResponse -> {
                HttpResponse result = httpResponse;
                if (result == null) {
                    result = httpOverrideForwardedRequest.getResponseOverride();
                } else {
                    result = result.update(httpOverrideForwardedRequest.getResponseOverride(), httpOverrideForwardedRequest.getResponseModifier());
                }
                if (responseTemplate != null && result != null) {
                    TemplateEngine templateEngine = resolveTemplateEngine(responseTemplate);
                    if (templateEngine != null) {
                        HttpResponse templatedResponse = templateEngine.executeTemplate(
                            responseTemplate.getTemplate(), request, result, HttpResponseDTO.class
                        );
                        if (templatedResponse != null) {
                            result = templatedResponse;
                        }
                    }
                }
                return result;
            });
        } else {
            return sendRequest(request, null, httpResponse -> httpResponse);
        }
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
        if (velocityTemplateEngine == null) {
            velocityTemplateEngine = new VelocityTemplateEngine(mockServerLogger, configuration);
        }
        return velocityTemplateEngine;
    }

    private JavaScriptTemplateEngine getJavaScriptTemplateEngine() {
        if (javascriptTemplateEngine == null) {
            javascriptTemplateEngine = new JavaScriptTemplateEngine(mockServerLogger, configuration);
        }
        return javascriptTemplateEngine;
    }

    private MustacheTemplateEngine getMustacheTemplateEngine() {
        if (mustacheTemplateEngine == null) {
            mustacheTemplateEngine = new MustacheTemplateEngine(mockServerLogger, configuration);
        }
        return mustacheTemplateEngine;
    }

}
