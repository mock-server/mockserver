package org.mockserver.templates.engine.velocity;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.templates.engine.SystemValueTemplateBehaviour;

import static org.mockserver.model.HttpRequest.request;

public class VelocitySystemValueTest extends SystemValueTemplateBehaviour<VelocityTemplateEngine> {

    @Override
    protected VelocityTemplateEngine engine(Configuration configuration, MockServerLogger mockServerLogger) {
        return new VelocityTemplateEngine(mockServerLogger, configuration);
    }

    @Override
    protected String env(VelocityTemplateEngine engine, String name) {
        return engine.renderTemplate("$systemValue.env('" + name + "')", request());
    }

    @Override
    protected String property(VelocityTemplateEngine engine, String name) {
        return engine.renderTemplate("$systemValue.property('" + name + "')", request());
    }
}
