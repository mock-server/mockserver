package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpResponseModifier;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

/**
 * @author jamesdbloom
 */
public class HttpForwardTemplateActionHandler extends HttpForwardAction {

    private volatile VelocityTemplateEngine velocityTemplateEngine;
    private volatile JavaScriptTemplateEngine javascriptTemplateEngine;
    private volatile MustacheTemplateEngine mustacheTemplateEngine;

    public HttpForwardTemplateActionHandler(MockServerLogger mockServerLogger, Configuration configuration, NettyHttpClient httpClient) {
        super(mockServerLogger, configuration, httpClient);
    }

    public HttpForwardActionResult handle(HttpTemplate httpTemplate, HttpRequest originalRequest) {
        TemplateEngine templateEngine = resolveTemplateEngine(httpTemplate);
        if (templateEngine != null) {
            HttpRequest templatedRequest = TemplateFailureException.render(httpTemplate, "forward", "request", templateFailureAsServerError(),
                () -> templateEngine.executeTemplate(httpTemplate.getTemplateContent(), httpTemplate.getParameters(), originalRequest, null, HttpRequestDTO.class));
            if (templatedRequest != null) {
                String originalHost = originalRequest.getFirstHeader("Host");
                String templatedHost = templatedRequest.getFirstHeader("Host");
                boolean templateExplicitlySetHost = templatedHost != null && !templatedHost.equals(originalHost);
                if (!templateExplicitlySetHost) {
                    adjustHostHeader(templatedRequest);
                }
                HttpResponse responseOverride = httpTemplate.getResponseOverride();
                HttpResponseModifier responseModifier = httpTemplate.getResponseModifier();
                if (responseOverride != null || responseModifier != null) {
                    return sendRequest(originalRequest, templatedRequest, null, httpResponse -> {
                        if (httpResponse == null) {
                            return responseOverride;
                        } else {
                            return httpResponse.update(responseOverride, responseModifier, originalRequest);
                        }
                    });
                }
                return sendRequest(originalRequest, templatedRequest, null, null);
            }
        }

        return badGatewayFuture(originalRequest);
    }

    @Override
    protected String actionName() {
        return "forward template action";
    }

    TemplateEngine resolveTemplateEngine(HttpTemplate httpTemplate) {
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
}
