package org.mockserver.templates.engine.javascript;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.logging.LogFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.templates.engine.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JavaScriptTemplateEngine implements TemplateEngine {

    private static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private static Logger logger = LoggerFactory.getLogger(JavaScriptTemplateEngine.class);
    private static LogFormatter logFormatter = new LogFormatter(logger);

    private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

    public HttpResponse executeTemplate(String template, HttpRequest httpRequest) {
        try {
            if (engine != null) {
                engine.eval("function handle(request) {" + template + "} function serialise(request) { return JSON.stringify(handle(JSON.parse(request)), null, 2); }");
                // HttpResponse handle(HttpRequest httpRequest) - ES5
                Object stringifiedResponse = ((Invocable) engine).invokeFunction("serialise", httpRequest);
                logFormatter.infoLog("Generated response:{}from template:{}for request:{}", stringifiedResponse, template, httpRequest);
                return objectMapper.readValue((String) stringifiedResponse, HttpResponseDTO.class).buildObject();
            } else {
                logger.error("JavaScript based templating is only available in a JVM with the \"nashorn\" JavaScript engine, " +
                        "please use a JVM with the \"nashorn\" JavaScript engine, such as Oracle Java 8+", new RuntimeException("\"nashorn\" JavaScript engine not available"));
            }
        } catch (Exception e) {
            logger.error("Exception transforming template:" + NEW_LINE + "\"" + template + "\" with request:" + NEW_LINE + httpRequest, e);
        }
        return null;
    }
}
