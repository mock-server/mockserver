package org.mockserver.templates.engine.javascript;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.model.DTO;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;

import javax.script.*;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.formatting.StringFormatter.indentAndToString;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.TEMPLATE_GENERATED;

/**
 * @author jamesdbloom
 */
public class JavaScriptTemplateEngine implements TemplateEngine {

    private static ScriptEngine engine;
    private final MockServerLogger logFormatter;
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer;

    public JavaScriptTemplateEngine(MockServerLogger logFormatter) {
        if (engine == null) {
            engine = new ScriptEngineManager().getEngineByName("nashorn");
        }
        this.logFormatter = logFormatter;
        this.httpTemplateOutputDeserializer = new HttpTemplateOutputDeserializer(logFormatter);
    }

    @Override
    public <T> T executeTemplate(String template, HttpRequest request, Class<? extends DTO<T>> dtoClass) {
        T result = null;
        String script = "function handle(request) {" + indentAndToString(template)[0] + "}";
        try {
            if (engine != null) {
                Compilable compilable = (Compilable) engine;
                CompiledScript compiledScript = compilable.compile(script + " function serialise(request) { return JSON.stringify(handle(JSON.parse(request)), null, 2); }");

                Bindings bindings = engine.createBindings();
                compiledScript.eval(bindings);

                ScriptObjectMirror scriptObjectMirror = (ScriptObjectMirror) bindings.get("serialise");
                Object stringifiedResponse = scriptObjectMirror.call(null, new HttpRequestTemplateObject(request));

                // HttpResponse handle(HttpRequest httpRequest) - ES5
                logFormatter.info(TEMPLATE_GENERATED, request, "generated output:{}from template:{}for request:{}", stringifiedResponse, script, request);
                result = httpTemplateOutputDeserializer.deserializer(request, (String) stringifiedResponse, dtoClass);
            } else {
                logFormatter.error(request, "JavaScript based templating is only available in a JVM with the \"nashorn\" JavaScript engine, " +
                    "please use a JVM with the \"nashorn\" JavaScript engine, such as Oracle Java 8+", new RuntimeException("\"nashorn\" JavaScript engine not available"));
            }
        } catch (Exception e) {
            throw new RuntimeException(formatLogMessage("Exception transforming template:{}for request:{}", script, request), e);
        }
        return result;
    }
}
