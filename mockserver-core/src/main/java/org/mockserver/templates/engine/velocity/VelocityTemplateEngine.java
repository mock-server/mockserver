package org.mockserver.templates.engine.velocity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.script.VelocityScriptEngine;
import org.apache.velocity.script.VelocityScriptEngineFactory;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.DTO;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.TemplateFunctions;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;
import org.mockserver.templates.engine.velocity.directives.Ifnull;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATED;
import static org.mockserver.log.model.LogEntryMessages.TEMPLATE_GENERATED_MESSAGE_FORMAT;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
public class VelocityTemplateEngine implements TemplateEngine {

    private static final Properties velocityProperties;
    private static final ScriptEngineManager manager;
    private static final ScriptEngine engine;
    private static ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer;

    static {
        // See: https://velocity.apache.org/engine/2.0/configuration.html
        velocityProperties = new Properties();
        velocityProperties.put("runtime.log.log_invalid_references", "true");
        velocityProperties.put("runtime.string_interning", "true");
        velocityProperties.put("directive.foreach.max_loops", "-1");
        velocityProperties.put("directive.if.empty_check", "true");
        velocityProperties.put("directive.parse.max_depth", "10");
        velocityProperties.put("context.scope_control.template", "false");
        velocityProperties.put("context.scope_control.evaluate", "false");
        velocityProperties.put("context.scope_control.foreach", "false");
        velocityProperties.put("context.scope_control.macro", "false");
        velocityProperties.put("context.scope_control.define", "false");
        velocityProperties.put("runtime.strict_mode.enable", "false");
        velocityProperties.put("runtime.interpolate_string_literals", "true");
        velocityProperties.put("resource.default_encoding", "UTF-8");
        velocityProperties.put("directive.set.null.allowed", "true");
        velocityProperties.put("parser.pool.class", "org.apache.velocity.runtime.ParserPoolImpl");
        velocityProperties.put("parser.pool.size", "50");
        velocityProperties.put("parser.space_gobbling", "lines");
        velocityProperties.put("parser.allow_hyphen_in_identifiers", "true");
        velocityProperties.put(RuntimeConstants.CUSTOM_DIRECTIVES, Ifnull.class.getName());
        manager = new ScriptEngineManager();
        manager.registerEngineName("velocity", new VelocityScriptEngineFactory());
        engine = manager.getEngineByName("velocity");
    }

    public VelocityTemplateEngine(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.httpTemplateOutputDeserializer = new HttpTemplateOutputDeserializer(mockServerLogger);
        if (objectMapper == null) {
            objectMapper = ObjectMapperFactory.createObjectMapper();
        }
    }

    @Override
    public <T> T executeTemplate(String template, HttpRequest request, Class<? extends DTO<T>> dtoClass) {
        T result;
        try {
            Writer writer = new StringWriter();
            ScriptContext context = new SimpleScriptContext();
            context.setWriter(writer);
            context.setAttribute(VelocityScriptEngine.VELOCITY_PROPERTIES_KEY, velocityProperties, ScriptContext.ENGINE_SCOPE);
            context.setAttribute("request", new HttpRequestTemplateObject(request), ScriptContext.ENGINE_SCOPE);
            TemplateFunctions.BUILT_IN_FUNCTIONS.forEach((key, value) -> context.setAttribute(key, value, ScriptContext.ENGINE_SCOPE));
            engine.eval(template, context);
            JsonNode generatedObject = null;
            try {
                generatedObject = objectMapper.readTree(writer.toString());
            } catch (Throwable throwable) {
                if (MockServerLogger.isEnabled(Level.TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.TRACE)
                            .setHttpRequest(request)
                            .setMessageFormat("exception deserialising generated content:{}into json node for request:{}")
                            .setArguments(writer.toString(), request)
                    );
                }
            }
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(TEMPLATE_GENERATED)
                        .setLogLevel(Level.INFO)
                        .setHttpRequest(request)
                        .setMessageFormat(TEMPLATE_GENERATED_MESSAGE_FORMAT)
                        .setArguments(generatedObject != null ? generatedObject : writer.toString(), template, request)
                );
            }
            result = httpTemplateOutputDeserializer.deserializer(request, writer.toString(), dtoClass);
        } catch (Exception e) {
            throw new RuntimeException(formatLogMessage("Exception:{}transforming template:{}for request:{}", isNotBlank(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName(), template, request), e);
        }
        return result;
    }
}
