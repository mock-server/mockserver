package org.mockserver.mock.action;

import org.mockserver.client.NettyHttpClient;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

/**
 * @author jamesdbloom
 */
public class HttpForwardTemplateActionHandler extends HttpForwardAction {

    private JavaScriptTemplateEngine javaScriptTemplateEngine;
    private VelocityTemplateEngine velocityTemplateEngine;

    public HttpForwardTemplateActionHandler(MockServerLogger mockServerLogger, NettyHttpClient httpClient) {
        super(mockServerLogger, httpClient);
    }

    public HttpForwardActionResult handle(HttpTemplate httpTemplate, HttpRequest originalRequest) {
        TemplateEngine templateEngine = null;
        switch (httpTemplate.getTemplateType()) {
            case VELOCITY:
                templateEngine = getVelocityTemplateEngine();
                break;
            case JAVASCRIPT:
                templateEngine = getJavaScriptTemplateEngine();
                break;
            default:
                throw new RuntimeException("Unknown no template engine available for " + httpTemplate.getTemplateType());
        }
        if (templateEngine != null) {
            HttpRequest templatedRequest = templateEngine.executeTemplate(httpTemplate.getTemplate(), originalRequest, HttpRequestDTO.class);
            if (templatedRequest != null) {
                return sendRequest(templatedRequest, null, null);
            }
        }

        return notFoundFuture(originalRequest);
    }

    private JavaScriptTemplateEngine getJavaScriptTemplateEngine() {
        if (javaScriptTemplateEngine == null) {
            javaScriptTemplateEngine = new JavaScriptTemplateEngine(mockServerLogger);
        }
        return javaScriptTemplateEngine;
    }

    private VelocityTemplateEngine getVelocityTemplateEngine() {
        if (velocityTemplateEngine == null) {
            velocityTemplateEngine = new VelocityTemplateEngine(mockServerLogger);
        }
        return velocityTemplateEngine;
    }
}
