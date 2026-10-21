package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateActionHandler {

    private final MockServerLogger mockServerLogger;
    private final Configuration configuration;
    private volatile VelocityTemplateEngine velocityTemplateEngine;
    private volatile JavaScriptTemplateEngine javascriptTemplateEngine;
    private volatile MustacheTemplateEngine mustacheTemplateEngine;

    public HttpResponseTemplateActionHandler(MockServerLogger mockServerLogger, Configuration configuration) {
        this.mockServerLogger = mockServerLogger;
        this.configuration = configuration;
    }

    public HttpResponse handle(HttpTemplate httpTemplate, HttpRequest httpRequest) {
        HttpResponse httpResponse = notFoundResponse();

        TemplateEngine templateEngine;
        switch (httpTemplate.getTemplateType()) {
            case VELOCITY:
                templateEngine = getVelocityTemplateEngine();
                break;
            case JAVASCRIPT:
                templateEngine = getJavaScriptTemplateEngine();
                break;
            case MUSTACHE:
                templateEngine = getMustacheTemplateEngine();
                break;
            default:
                throw new RuntimeException("Unknown no template engine available for " + httpTemplate.getTemplateType());
        }
        if (templateEngine != null) {
            TemplateEngine engine = templateEngine;
            HttpResponse templatedResponse = TemplateFailureException.render(httpTemplate, "response", "response", Boolean.TRUE.equals(configuration.templateFailureAsServerError()),
                () -> engine.executeTemplate(httpTemplate.getTemplateContent(), httpTemplate.getParameters(), httpRequest, null, HttpResponseDTO.class));
            if (templatedResponse != null) {
                return templatedResponse;
            }
        }

        return httpResponse;
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
