package org.mockserver.templates.engine.javascript;

import org.mockserver.client.serialization.model.DTO;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static Logger logger = LoggerFactory.getLogger(JavaScriptTemplateEngine.class);
    private static LoggingFormatter logFormatter = new LoggingFormatter(logger);
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer = new HttpTemplateOutputDeserializer();

    public <T> T executeTemplate(String template, HttpRequest httpRequest, Class<? extends DTO<T>> dtoClass) {
        String script = "function handle(request) {" + indentAndToString(template)[0] + "}";
        try {
            if (engine != null) {
                engine.eval(script + " function serialise(request) { return JSON.stringify(handle(JSON.parse(request)), null, 2); }");
                // HttpResponse handle(HttpRequest httpRequest) - ES5
                Object stringifiedResponse = ((Invocable) engine).invokeFunction("serialise", new HttpRequestTemplateObject(httpRequest));
                logFormatter.infoLog("Generated output:{}" + NEW_LINE + " from template:{}" + NEW_LINE + " for request:{}", stringifiedResponse, script, httpRequest);
                return httpTemplateOutputDeserializer.deserializer((String) stringifiedResponse, dtoClass);
            } else {
                logger.error("JavaScript based templating is only available in a JVM with the \"nashorn\" JavaScript engine, " +
                        "please use a JVM with the \"nashorn\" JavaScript engine, such as Oracle Java 8+", new RuntimeException("\"nashorn\" JavaScript engine not available"));
            }
        } catch (Exception e) {
            throw new RuntimeException(formatLogMessage("Exception transforming template:{}" + NEW_LINE + " for request:{}", script, httpRequest).toString(), e);
        }
        return null;
    }
}
