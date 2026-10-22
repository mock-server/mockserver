package org.mockserver.templates.engine.mustache;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.templates.engine.SystemValueTemplateBehaviour;

import static org.mockserver.model.HttpRequest.request;

public class MustacheSystemValueTest extends SystemValueTemplateBehaviour<MustacheTemplateEngine> {

    @Override
    protected MustacheTemplateEngine engine(Configuration configuration, MockServerLogger mockServerLogger) {
        return new MustacheTemplateEngine(mockServerLogger, configuration);
    }

    @Override
    protected String env(MustacheTemplateEngine engine, String name) {
        return engine.renderTemplate("{{#systemValue.env}} " + name + " {{/systemValue.env}}", request());
    }

    @Override
    protected String property(MustacheTemplateEngine engine, String name) {
        return engine.renderTemplate("{{#systemValue.property}}" + name + "{{/systemValue.property}}", request());
    }
}
