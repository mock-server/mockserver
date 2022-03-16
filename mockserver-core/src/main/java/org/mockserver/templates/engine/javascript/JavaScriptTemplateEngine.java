package org.mockserver.templates.engine.javascript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.DTO;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.TemplateFunctions;
import org.mockserver.templates.engine.javascript.bindings.ScriptBindings;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import javax.script.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.formatting.StringFormatter.indentAndToString;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATED;
import static org.mockserver.log.model.LogEntryMessages.TEMPLATE_GENERATED_MESSAGE_FORMAT;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"RedundantSuppression", "deprecation", "removal", "FieldMayBeFinal"})
public class JavaScriptTemplateEngine implements TemplateEngine {

    private static ScriptEngine engine;
    private static ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer;

    public JavaScriptTemplateEngine(MockServerLogger mockServerLogger) {
        System.setProperty("nashorn.args", "--language=es6");
        if (engine == null) {
            engine = new ScriptEngineManager().getEngineByName("nashorn");
        }
        this.mockServerLogger = mockServerLogger;
        this.httpTemplateOutputDeserializer = new HttpTemplateOutputDeserializer(mockServerLogger);
        if (objectMapper == null) {
            objectMapper = ObjectMapperFactory.createObjectMapper();
        }
    }

    @Override
    public <T> T executeTemplate(String template, HttpRequest request, Class<? extends DTO<T>> dtoClass) {
        T result = null;
        String script = wrapTemplate(template);
        try {
            if (engine != null) {
                Compilable compilable = (Compilable) engine;
                // HttpResponse handle(HttpRequest httpRequest) - ES6
                CompiledScript compiledScript = compilable.compile(script + " function serialise(request) { return JSON.stringify(handle(JSON.parse(request)), null, 2); }");

                Bindings serialiseBindings = engine.createBindings();
                engine.setBindings(new ScriptBindings(TemplateFunctions.BUILT_IN_FUNCTIONS), ScriptContext.ENGINE_SCOPE);
                compiledScript.eval(serialiseBindings);

                ScriptObjectMirror scriptObjectMirror = (ScriptObjectMirror) serialiseBindings.get("serialise");
                Object stringifiedResponse = scriptObjectMirror.call(null, new HttpRequestTemplateObject(request));

                JsonNode generatedObject = null;
                try {
                    generatedObject = objectMapper.readTree(String.valueOf(stringifiedResponse));
                } catch (Throwable throwable) {
                    if (MockServerLogger.isEnabled(Level.INFO)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(request)
                                .setMessageFormat("exception deserialising generated content:{}into json node for request:{}")
                                .setArguments(stringifiedResponse, request)
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
                            .setArguments(generatedObject != null ? generatedObject : stringifiedResponse, script, request)
                    );
                }
                result = httpTemplateOutputDeserializer.deserializer(request, (String) stringifiedResponse, dtoClass);
            } else {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setHttpRequest(request)
                        .setMessageFormat(
                            "JavaScript based templating is only available in a JVM with the \"nashorn\" JavaScript engine, " +
                                "please use a JVM with the \"nashorn\" JavaScript engine, such as Oracle Java 8+"
                        )
                        .setArguments(new RuntimeException("\"nashorn\" JavaScript engine not available"))
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(formatLogMessage("Exception:{}transforming template:{}for request:{}", isNotBlank(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName(), template, request), e);
        }
        return result;
    }

    static String wrapTemplate(String template) {
        return "function handle(request) {" + indentAndToString(template)[0] + "}";
    }
}
