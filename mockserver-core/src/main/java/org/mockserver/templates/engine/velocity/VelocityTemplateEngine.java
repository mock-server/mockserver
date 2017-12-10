package org.mockserver.templates.engine.velocity;

import org.apache.velocity.script.VelocityScriptEngineFactory;
import org.mockserver.client.serialization.model.DTO;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.StringWriter;
import java.io.Writer;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;

/**
 * @author jamesdbloom
 */
public class VelocityTemplateEngine implements TemplateEngine {

    private static final ScriptEngineManager manager = new ScriptEngineManager();
    private static final ScriptEngine engine;
    private final LoggingFormatter logFormatter;
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer;

    static {
        manager.registerEngineName("velocity", new VelocityScriptEngineFactory());
        engine = manager.getEngineByName("velocity");
    }

    public VelocityTemplateEngine(LoggingFormatter logFormatter) {
        this.logFormatter = logFormatter;
        this.httpTemplateOutputDeserializer = new HttpTemplateOutputDeserializer(logFormatter);
    }

    @Override
    public <T> T executeTemplate(String template, HttpRequest request, Class<? extends DTO<T>> dtoClass) {
        T result = null;
        try {
            Writer writer = new StringWriter();
            ScriptContext context = engine.getContext();
            context.setWriter(writer);
            context.setAttribute("request", new HttpRequestTemplateObject(request), ScriptContext.ENGINE_SCOPE);
            engine.eval(template);
            logFormatter.infoLog(request, "Generated output:{}" + NEW_LINE + " from template:{}" + NEW_LINE + " for request:{}", writer.toString(), template, request);
            result = httpTemplateOutputDeserializer.deserializer(request, writer.toString(), dtoClass);
        } catch (Exception e) {
            throw new RuntimeException(formatLogMessage("Exception transforming template:{}" + NEW_LINE + " for request:{}", template, request), e);
        }
        return result;
    }
}
