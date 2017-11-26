package org.mockserver.templates.engine.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.velocity.script.VelocityScriptEngineFactory;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.logging.LogFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.velocity.model.HttpRequestTemplateObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.StringWriter;
import java.io.Writer;

/**
 * @author jamesdbloom
 */
public class VelocityTemplateEngine implements TemplateEngine {
    private static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private static Logger logger = LoggerFactory.getLogger(VelocityTemplateEngine.class);
    private static LogFormatter logFormatter = new LogFormatter(logger);

    private static final ScriptEngineManager manager = new ScriptEngineManager();
    private static final ScriptEngine engine;

    static {
        manager.registerEngineName("velocity", new VelocityScriptEngineFactory());
        engine = manager.getEngineByName("velocity");
    }

    @Override
    public HttpResponse executeTemplate(String template, HttpRequest httpRequest) {
        try {
            Writer writer = new StringWriter();
            ScriptContext context = engine.getContext();
            context.setWriter(writer);
            context.setAttribute("request", new HttpRequestTemplateObject(httpRequest), ScriptContext.ENGINE_SCOPE);
            engine.eval(template);
            logFormatter.infoLog("Generated response:{}from template:{}for request:{}", writer.toString(), template, httpRequest);
            return objectMapper.readValue(writer.toString(), HttpResponseDTO.class).buildObject();
        } catch (Exception e) {
            logFormatter.errorLog(e, "Exception transforming template:{}for request:{}", template, httpRequest);
        }
        return null;
    }
}
