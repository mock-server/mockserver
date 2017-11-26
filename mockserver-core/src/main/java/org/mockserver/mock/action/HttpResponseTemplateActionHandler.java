package org.mockserver.mock.action;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateActionHandler {

    private JavaScriptTemplateEngine javaScriptTemplateEngine = new JavaScriptTemplateEngine();
    private VelocityTemplateEngine velocityTemplateEngine = new VelocityTemplateEngine();

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
            HttpResponse stringifiedResponse = templateEngine.executeTemplate(httpTemplate.getTemplate(), httpRequest);
            if (stringifiedResponse != null) {
                return stringifiedResponse;
            }
        }

        httpTemplate.applyDelay();
        return httpResponse;
    }

}
