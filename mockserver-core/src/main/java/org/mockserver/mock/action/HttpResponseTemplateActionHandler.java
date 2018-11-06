package org.mockserver.mock.action;

import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateActionHandler {

    private JavaScriptTemplateEngine javaScriptTemplateEngine;
    private VelocityTemplateEngine velocityTemplateEngine;

    public HttpResponseTemplateActionHandler(MockServerLogger logFormatter) {
        javaScriptTemplateEngine = new JavaScriptTemplateEngine(logFormatter);
        velocityTemplateEngine = new VelocityTemplateEngine(logFormatter);
    }

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
            HttpResponse templatedResponse = templateEngine.executeTemplate(httpTemplate.getTemplate(), httpRequest, HttpResponseDTO.class);
            if (templatedResponse != null) {
                return templatedResponse;
            }
        }

        return httpResponse;
    }

}
