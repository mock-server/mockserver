package org.mockserver.mock.action;

import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class HttpForwardTemplateActionHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private JavaScriptTemplateEngine javaScriptTemplateEngine = new JavaScriptTemplateEngine();
    private VelocityTemplateEngine velocityTemplateEngine = new VelocityTemplateEngine();
    private NettyHttpClient httpClient = new NettyHttpClient();

    public HttpResponse handle(HttpTemplate httpTemplate, HttpRequest httpRequest) {
        HttpResponse httpResponse = notFoundResponse();

        TemplateEngine templateEngine = null;
        switch (httpTemplate.getTemplateType()) {
            case VELOCITY:
                templateEngine = velocityTemplateEngine;
                break;
            case JAVASCRIPT:
                templateEngine = javaScriptTemplateEngine;
                break;
            default:
                throw new RuntimeException("Unknown no template engine available for " + httpTemplate.getTemplateType());
        }
        if (templateEngine != null) {
            HttpRequest templatedRequest = templateEngine.executeTemplate(httpTemplate.getTemplate(), httpRequest, HttpRequestDTO.class);
            if (templatedRequest != null) {
                httpResponse = sendRequest(templatedRequest);
            }
        }

        httpTemplate.applyDelay();
        return httpResponse;
    }

    private HttpResponse sendRequest(HttpRequest httpRequest) {
        if (httpRequest != null) {
            try {
                return httpClient.sendRequest(httpRequest);
            } catch (Exception e) {
                logger.error("Exception forwarding request " + httpRequest, e);
            }
        }
        return null;
    }
}
