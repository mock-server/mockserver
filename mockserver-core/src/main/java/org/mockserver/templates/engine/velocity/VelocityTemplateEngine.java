package org.mockserver.templates.engine.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.velocity.script.VelocityScriptEngineFactory;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.templates.engine.TemplateEngine;
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
    private final static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final static Logger logger = LoggerFactory.getLogger(VelocityTemplateEngine.class);

    private final ScriptEngineManager manager = new ScriptEngineManager();
    private final ScriptEngine engine = manager.getEngineByName("velocity");

    public VelocityTemplateEngine() {
        manager.registerEngineName("velocity", new VelocityScriptEngineFactory());
//            System.setProperty(VelocityScriptEngine.VELOCITY_PROPERTIES_KEY, "path/to/velocity.properties");
    }

    @Override
    public HttpResponse executeTemplate(String template, HttpRequest httpRequest) {
        try {
            Writer writer = new StringWriter();
            ScriptContext context = engine.getContext();
            context.setWriter(writer);
            context.setAttribute("request", new HttpRequestDTO(httpRequest), ScriptContext.ENGINE_SCOPE);
            engine.eval(template);
            return objectMapper.readValue(writer.toString(), HttpResponseDTO.class).buildObject();
        } catch (Exception e) {
            logger.error("Exception transforming template:\n\"" + template + "\" with request:\n" + httpRequest, e);
        }
        return null;
    }
}
