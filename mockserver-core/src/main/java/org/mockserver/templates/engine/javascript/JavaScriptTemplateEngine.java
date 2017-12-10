package org.mockserver.templates.engine.javascript;

import org.mockserver.client.serialization.model.DTO;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.formatting.StringFormatter.indentAndToString;

/**
 * @author jamesdbloom
 */
public class JavaScriptTemplateEngine implements TemplateEngine {

    private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
    private final LoggingFormatter logFormatter;
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer;

    public JavaScriptTemplateEngine(LoggingFormatter logFormatter) {
        this.logFormatter = logFormatter;
        this.httpTemplateOutputDeserializer = new HttpTemplateOutputDeserializer(logFormatter);
    }

    @Override
    public <T> T executeTemplate(String template, HttpRequest request, Class<? extends DTO<T>> dtoClass) {
        T result = null;
        String script = "function handle(request) {" + indentAndToString(template)[0] + "}";
        try {
            if (engine != null) {
                engine.eval(script + " function serialise(request) { return JSON.stringify(handle(JSON.parse(request)), null, 2); }");
                // HttpResponse handle(HttpRequest httpRequest) - ES5
                Object stringifiedResponse = ((Invocable) engine).invokeFunction("serialise", new HttpRequestTemplateObject(request));
                logFormatter.infoLog(request, "Generated output:{}" + NEW_LINE + " from template:{}" + NEW_LINE + " for request:{}", stringifiedResponse, script, request);
                result = httpTemplateOutputDeserializer.deserializer(request, (String) stringifiedResponse, dtoClass);
            } else {
                logFormatter.errorLog(request, "JavaScript based templating is only available in a JVM with the \"nashorn\" JavaScript engine, " +
                    "please use a JVM with the \"nashorn\" JavaScript engine, such as Oracle Java 8+", new RuntimeException("\"nashorn\" JavaScript engine not available"));
            }
        } catch (Exception e) {
            throw new RuntimeException(formatLogMessage("Exception transforming template:{}" + NEW_LINE + " for request:{}", script, request), e);
        }
        return result;
    }
}
