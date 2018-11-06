package org.mockserver.templates.engine.velocity;

import org.apache.velocity.script.VelocityScriptEngineFactory;
import org.mockserver.serialization.model.DTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.StringWriter;
import java.io.Writer;

import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.TEMPLATE_GENERATED;

/**
 * @author jamesdbloom
 */
public class VelocityTemplateEngine implements TemplateEngine {

    private static final ScriptEngineManager manager = new ScriptEngineManager();
    private static final ScriptEngine engine;
    private final MockServerLogger logFormatter;
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer;

    static {
        manager.registerEngineName("velocity", new VelocityScriptEngineFactory());
        engine = manager.getEngineByName("velocity");
    }

    public VelocityTemplateEngine(MockServerLogger logFormatter) {
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
            logFormatter.info(TEMPLATE_GENERATED, request, "generated output:{}from template:{}for request:{}", writer.toString(), template, request);
            result = httpTemplateOutputDeserializer.deserializer(request, writer.toString(), dtoClass);
        } catch (Exception e) {
            throw new RuntimeException(formatLogMessage("Exception transforming template:{}for request:{}", template, request), e);
        }
        return result;
    }
}
