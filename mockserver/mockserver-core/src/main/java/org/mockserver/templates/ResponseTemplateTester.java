package org.mockserver.templates;

import org.apache.commons.lang3.NotImplementedException;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

import javax.script.ScriptEngineManager;

public class ResponseTemplateTester {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(ResponseTemplateTester.class);

    public static HttpResponse testMustacheTemplate(String template, HttpRequest request) {
        return new MustacheTemplateEngine(MOCK_SERVER_LOGGER, new Configuration()).executeTemplate(template, request, HttpResponseDTO.class);
    }

    public static HttpResponse testVelocityTemplate(String template, HttpRequest request) {
        return new VelocityTemplateEngine(MOCK_SERVER_LOGGER, new Configuration()).executeTemplate(template, request, HttpResponseDTO.class);
    }

    public static HttpResponse testJavaScriptTemplate(String template, HttpRequest request) {
        if (isJavaScriptEngineAvailable()) {
            return new JavaScriptTemplateEngine(MOCK_SERVER_LOGGER, new Configuration()).executeTemplate(template, request, HttpResponseDTO.class);
        } else {
            throw new NotImplementedException("No JavaScript engine (Nashorn or GraalJS) is available on this JVM");
        }
    }

    private static boolean isJavaScriptEngineAvailable() {
        ScriptEngineManager manager = new ScriptEngineManager();
        return manager.getEngineByName("nashorn") != null
            || manager.getEngineByName("graal.js") != null
            || manager.getEngineByName("js") != null;
    }

}
