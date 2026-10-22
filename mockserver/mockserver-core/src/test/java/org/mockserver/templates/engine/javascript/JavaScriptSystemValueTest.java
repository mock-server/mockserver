package org.mockserver.templates.engine.javascript;

import org.junit.Before;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.SystemValueTemplateBehaviour;

import static org.junit.Assume.assumeTrue;
import static org.mockserver.model.HttpRequest.request;

public class JavaScriptSystemValueTest extends SystemValueTemplateBehaviour<JavaScriptTemplateEngine> {

    @Before
    public void requirePolyglot() {
        assumeTrue("GraalVM Polyglot API available", JavaScriptTemplateEngine.isPolyglotAvailable());
    }

    @Override
    protected JavaScriptTemplateEngine engine(Configuration configuration, MockServerLogger mockServerLogger) {
        return new JavaScriptTemplateEngine(mockServerLogger, configuration.javascriptTemplateExecutionTimeout(0L));
    }

    @Override
    protected String env(JavaScriptTemplateEngine engine, String name) {
        return body(engine, "systemValue.env('" + name + "')");
    }

    @Override
    protected String property(JavaScriptTemplateEngine engine, String name) {
        return body(engine, "systemValue.property('" + name + "')");
    }

    private static String body(JavaScriptTemplateEngine engine, String expression) {
        return engine
            .executeTemplate("return { 'statusCode': 200, 'body': 'value=' + " + expression + " };", null, request(), null, HttpResponseDTO.class)
            .getBodyAsString()
            .substring("value=".length());
    }
}
