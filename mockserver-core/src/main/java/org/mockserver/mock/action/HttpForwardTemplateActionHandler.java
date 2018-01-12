package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

import java.util.Arrays;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class HttpForwardTemplateActionHandler extends HttpForwardAction {

    private JavaScriptTemplateEngine javaScriptTemplateEngine;
    private VelocityTemplateEngine velocityTemplateEngine;

    public HttpForwardTemplateActionHandler(LoggingFormatter logFormatter) {
        super(logFormatter);
        javaScriptTemplateEngine = new JavaScriptTemplateEngine(logFormatter);
        velocityTemplateEngine = new VelocityTemplateEngine(logFormatter);
    }

    public SettableFuture<HttpResponse> handle(HttpTemplate httpTemplate, HttpRequest originalRequest) {
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
            HttpRequest templatedRequest = templateEngine.executeTemplate(httpTemplate.getTemplate(), originalRequest, HttpRequestDTO.class);
            if (templatedRequest != null) {
                return sendRequest(templatedRequest, null);
            }
        }

        return notFoundFuture();
    }
}
