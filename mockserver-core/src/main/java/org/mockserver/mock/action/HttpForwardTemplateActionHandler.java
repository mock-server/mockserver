package org.mockserver.mock.action;

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
public class HttpForwardTemplateActionHandler {

    private final LoggingFormatter logFormatter;
    private JavaScriptTemplateEngine javaScriptTemplateEngine;
    private VelocityTemplateEngine velocityTemplateEngine;
    private NettyHttpClient httpClient = new NettyHttpClient();
    private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();

    public HttpForwardTemplateActionHandler(LoggingFormatter logFormatter) {
        this.logFormatter = logFormatter;
        javaScriptTemplateEngine = new JavaScriptTemplateEngine(logFormatter);
        velocityTemplateEngine = new VelocityTemplateEngine(logFormatter);
    }

    public HttpResponse handle(HttpTemplate httpTemplate, HttpRequest originalRequest) {
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
            HttpRequest templatedRequest = templateEngine.executeTemplate(httpTemplate.getTemplate(), originalRequest, HttpRequestDTO.class);
            if (templatedRequest != null) {
                httpResponse = sendRequest(originalRequest, templatedRequest);
            }
        }

        return httpResponse;
    }

    private HttpResponse sendRequest(HttpRequest originalRequest, HttpRequest templatedRequest) {
        templatedRequest = hopByHopHeaderFilter.onRequest(templatedRequest);
        if (templatedRequest != null) {
            try {
                return httpClient.sendRequest(templatedRequest);
            } catch (Exception e) {
                logFormatter.errorLog(Arrays.asList(originalRequest, templatedRequest), e, "Exception forwarding request " + templatedRequest);
            }
        }
        return null;
    }
}
