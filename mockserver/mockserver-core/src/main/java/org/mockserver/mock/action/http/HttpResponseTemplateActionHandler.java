package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.extension.TemplateHelpers;
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
    // non-null only for the handler of primary response templates when an extension offers template helpers
    private final TemplateHelpers templateHelpers;

    public HttpResponseTemplateActionHandler(MockServerLogger mockServerLogger, Configuration configuration) {
        this(mockServerLogger, configuration, null);
    }

    /**
     * A handler whose templates can call the extension template helpers in {@code templateHelpers}; each render ends
     * the helper budget it used.
     */
    public HttpResponseTemplateActionHandler(MockServerLogger mockServerLogger, Configuration configuration, TemplateHelpers templateHelpers) {
        this.mockServerLogger = mockServerLogger;
        this.configuration = configuration;
        this.templateHelpers = templateHelpers;
    }

    public HttpResponse handle(HttpTemplate httpTemplate, HttpRequest httpRequest) {
        if (templateHelpers == null) {
            return render(httpTemplate, httpRequest);
        }
        try {
            return render(httpTemplate, httpRequest);
        } finally {
            TemplateHelpers.endRender();
        }
    }

    private HttpResponse render(HttpTemplate httpTemplate, HttpRequest httpRequest) {
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
                    engine = velocityTemplateEngine = templateHelpers != null
                        ? new VelocityTemplateEngine(mockServerLogger, configuration, templateHelpers.namespace())
                        : new VelocityTemplateEngine(mockServerLogger, configuration);
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
                    engine = javascriptTemplateEngine = templateHelpers != null
                        ? new JavaScriptTemplateEngine(mockServerLogger, configuration, templateHelpers.namespace())
                        : new JavaScriptTemplateEngine(mockServerLogger, configuration);
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
                    engine = mustacheTemplateEngine = templateHelpers != null
                        ? new MustacheTemplateEngine(mockServerLogger, configuration, templateHelpers.mustacheNamespace())
                        : new MustacheTemplateEngine(mockServerLogger, configuration);
                }
            }
        }
        return engine;
    }

}
