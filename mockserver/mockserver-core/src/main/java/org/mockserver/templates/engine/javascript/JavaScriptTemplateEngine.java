package org.mockserver.templates.engine.javascript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.DTO;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.TemplateFunctions;
import org.mockserver.templates.engine.javascript.bindings.ScriptBindings;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.templates.engine.model.HttpResponseTemplateObject;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;
import org.slf4j.event.Level;

import javax.script.*;
import java.util.stream.StreamSupport;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.formatting.StringFormatter.indentAndToString;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATED;
import static org.mockserver.log.model.LogEntryMessages.TEMPLATE_GENERATED_MESSAGE_FORMAT;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"RedundantSuppression", "deprecation", "removal", "FieldMayBeFinal"})
public class JavaScriptTemplateEngine implements TemplateEngine {

    private static final String ENGINE_NASHORN = "nashorn";
    private static final String ENGINE_GRAALJS = "graal.js";

    private ScriptEngine engine;
    private ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer;
    private final Configuration configuration;

    public JavaScriptTemplateEngine(MockServerLogger mockServerLogger, Configuration configuration) {
        this.configuration = (configuration == null) ? configuration() : configuration;
        this.engine = createEngine(this.configuration);
        this.mockServerLogger = mockServerLogger;
        this.httpTemplateOutputDeserializer = new HttpTemplateOutputDeserializer(mockServerLogger);
        this.objectMapper = ObjectMapperFactory.createObjectMapper();
    }

    @SuppressWarnings("unchecked")
    private static ScriptEngine createEngine(Configuration configuration) {
        try {
            Class<?> nashornFactoryClass = JavaScriptTemplateEngine.class.getClassLoader()
                .loadClass("org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory");
            Class<?> classFilterClass = JavaScriptTemplateEngine.class.getClassLoader()
                .loadClass("org.openjdk.nashorn.api.scripting.ClassFilter");
            System.setProperty("nashorn.args", "--language=es6");
            Object factory = nashornFactoryClass.getConstructor().newInstance();
            Object classFilter = java.lang.reflect.Proxy.newProxyInstance(
                JavaScriptTemplateEngine.class.getClassLoader(),
                new Class<?>[]{classFilterClass},
                (proxy, method, args) -> {
                    if ("exposeToScripts".equals(method.getName())) {
                        return isClassAllowed((String) args[0], configuration);
                    }
                    return null;
                }
            );
            java.lang.reflect.Method getScriptEngine = nashornFactoryClass.getMethod("getScriptEngine", classFilterClass);
            return (ScriptEngine) getScriptEngine.invoke(factory, classFilter);
        } catch (Throwable ignore) {
            // fall through to GraalJS
        }

        ScriptEngine graalEngine = new ScriptEngineManager().getEngineByName(ENGINE_GRAALJS);
        if (graalEngine == null) {
            graalEngine = new ScriptEngineManager().getEngineByName("js");
        }
        if (graalEngine != null) {
            Bindings bindings = graalEngine.getBindings(ScriptContext.ENGINE_SCOPE);
            bindings.put("polyglot.js.allowHostAccess", true);
            bindings.put("polyglot.js.allowHostClassLookup", (java.util.function.Predicate<String>) className ->
                isClassAllowed(className, configuration));
        }
        return graalEngine;
    }

    private static boolean isClassAllowed(String className, Configuration configuration) {
        if (isNotBlank(configuration.javascriptDisallowedClasses())) {
            Iterable<String> restrictedClasses = Splitter.on(",").trimResults().split(configuration.javascriptDisallowedClasses());
            return StreamSupport.stream(restrictedClasses.spliterator(), false)
                .noneMatch(restrictedClass -> restrictedClass.equalsIgnoreCase(className));
        }
        return true;
    }

    @Override
    public synchronized <T> T executeTemplate(String template, HttpRequest request, Class<? extends DTO<T>> dtoClass) {
        return executeTemplateInternal(template, request, null, dtoClass, false);
    }

    @Override
    public synchronized <T> T executeTemplate(String template, HttpRequest request, HttpResponse response, Class<? extends DTO<T>> dtoClass) {
        return executeTemplateInternal(template, request, response, dtoClass, true);
    }

    private synchronized <T> T executeTemplateInternal(String template, HttpRequest request, HttpResponse response, Class<? extends DTO<T>> dtoClass, boolean includeResponse) {
        T result = null;
        String script = includeResponse ? wrapTemplateWithResponse(template) : wrapTemplate(template);
        try {
            validateTemplate(template);
            if (engine != null) {
                Compilable compilable = (Compilable) engine;
                String serialiseFunction = includeResponse
                    ? " function serialise(request, response) { return JSON.stringify(handle(JSON.parse(request), JSON.parse(response)), null, 2); }"
                    : " function serialise(request) { return JSON.stringify(handle(JSON.parse(request)), null, 2); }";
                CompiledScript compiledScript = compilable.compile(script + serialiseFunction);

                engine.setBindings(new ScriptBindings(TemplateFunctions.BUILT_IN_FUNCTIONS), ScriptContext.ENGINE_SCOPE);
                TemplateFunctions.BUILT_IN_HELPERS.forEach((key, value) -> engine.getBindings(ScriptContext.ENGINE_SCOPE).put(key, value));
                compiledScript.eval();

                Invocable invocable = (Invocable) engine;
                Object stringifiedResponse;
                if (includeResponse) {
                    stringifiedResponse = invocable.invokeFunction("serialise", new HttpRequestTemplateObject(request), new HttpResponseTemplateObject(response));
                } else {
                    stringifiedResponse = invocable.invokeFunction("serialise", new HttpRequestTemplateObject(request));
                }

                JsonNode generatedObject = null;
                try {
                    generatedObject = objectMapper.readTree(String.valueOf(stringifiedResponse));
                } catch (Throwable throwable) {
                    if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(request)
                                .setMessageFormat("exception deserialising generated content:{}into json node for request:{}")
                                .setArguments(stringifiedResponse, request)
                        );
                    }
                }
                if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
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
                            "JavaScript based templating requires a JavaScript engine on the classpath, " +
                                "please add nashorn-core or GraalJS to the classpath"
                        )
                        .setArguments(new RuntimeException("no JavaScript engine available"))
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

    static String wrapTemplateWithResponse(String template) {
        return "function handle(request, response) {" + indentAndToString(template)[0] + "}";
    }

    private void validateTemplate(String template) {
        if (isNotBlank(template) && isNotBlank(configuration.javascriptDisallowedText())) {
            Iterable<String> deniedStrings = Splitter.on(",").trimResults().split(configuration.javascriptDisallowedText());
            for (String deniedString : deniedStrings) {
                if (template.contains(deniedString)) {
                    throw new UnsupportedOperationException("Found disallowed string \"" + deniedString + "\" in template: " + template);
                }
            }
        }
    }

}
