package org.mockserver.templates.engine.javascript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.mockserver.configuration.Configuration;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

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

    private static ScriptEngine engine;
    private static ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer;
    private static Configuration configuration;

    public JavaScriptTemplateEngine(MockServerLogger mockServerLogger) {
        this(mockServerLogger, null);
    }

    public JavaScriptTemplateEngine(MockServerLogger mockServerLogger, Configuration _configuration) {
        System.setProperty("nashorn.args", "--language=es6");
        configuration = (_configuration == null) ? configuration() : _configuration;
        if (engine == null) {
            engine = new NashornScriptEngineFactory().getScriptEngine(new SecureFilter());
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
            if (!validateTemplate(template)) {
                throw new UnsupportedOperationException("Invalid template string specified: " + template);
            }
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

    /**
     * Mockserver provides option for users to execute custom javascript templates.
     * However, there are possibilities where a user can inject a malicious code or access any java objects
     * Mockserver sets this ClassFilter instance when an engine instance is created.
     *
     * Mockserver property "mockserver.javascript.text.deny" can be set to specify the list of restricted strings.
     * This property takes a list of restricted text strings (use comma as separator to specify more than one restricted text).
     * Ex: mockserver.javascript.text.deny=engine.factory will deny execution of the javascript if the template contains the string engine.factory
     */
    boolean validateTemplate(String template) {
        if (template == null) {
            return true;
        }

        try {
            String restrictedText = configuration.javaScriptDeniedText();
            if ((restrictedText != null) && (restrictedText.trim().length() > 0)) {
                String[] restrictedTextElements = (restrictedText.indexOf(",") > -1) ? restrictedText.split(",") : new String[] {restrictedText};
                for (String restrictedTextElement : restrictedTextElements) {
                    if (template.indexOf(restrictedTextElement) > -1) {
                        return false;
                    }
                }
                return true;
            }

        } catch (Throwable t) {
            //skip if we can't validate the template...
        }
        return true;
    }

    /**
     * Class filter to be used by nashorn script engine.
     * Mockserver uses nashorn script engine to run javascript.
     * Mockserver sets this ClassFilter instance when an engine instance is created.
     *
     * Mockserver property "mockserver.javascript.class.deny" can be set to specify the list of restricted classnames.
     * This property takes a list of java classnames (use comma as separator to specify more than one class).
     * If this property is not set, or has the value as *... it exposes any java class to javascript
     * Ex: mockserver.javascript.class.deny=java.lang.Runtime will deny exposing java.lang.Runtime class to javascript, while all other classes will be exposed.
     */
    static class SecureFilter implements ClassFilter {
        ArrayList<String> restrictedClassesList = null;

        SecureFilter() {
            init();
        }

        void init() {
            String restrictedClasses = configuration.javaScriptDeniedClasses();
            if (restrictedClassesList == null) {
                if ((restrictedClasses != null) && (restrictedClasses.trim().length() > 0)) {
                    restrictedClassesList = new ArrayList<String>();
                    restrictedClassesList.addAll(Arrays.asList(restrictedClasses.split(",")));
                }
            }
        }

        @Override
        /**
         * Specifies whether the Java class of the specified name be exposed to javascript
         * @param className is the fully qualified name of the java class being checked.
         *                  This will not be null. Only non-array class names will be passed.
         * @return true if the java class can be exposed to javascript, false otherwise
         */
        public boolean exposeToScripts(String className) {
            if ((restrictedClassesList == null) || (restrictedClassesList.size() < 1) ||  restrictedClassesList.contains("*")) {
                return true;
            }

            if (restrictedClassesList.contains(className)) {
                return false;
            }

            return true;
        }
    }

    public static void clear() {
        engine = null;
        configuration = null;
        objectMapper = null;
    }

}
