package org.mockserver.mock.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateActionHandler {
    private final static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final static Logger logger = LoggerFactory.getLogger(HttpResponseTemplateActionHandler.class);

    private final ScriptEngine engine;
    private ScriptObjectMirror jsonJavaScriptObject;

    public HttpResponseTemplateActionHandler() {
        engine = new ScriptEngineManager().getEngineByName("nashorn");
        if (engine != null) {
            try {
                jsonJavaScriptObject = (ScriptObjectMirror) engine.eval("JSON");
            } catch (ScriptException se) {
                logger.error("Exception initialising JavaScript script engine", se);
            }
        }
    }

    public static void main(String[] args) {
        HttpTemplate template = template("if (request.method === 'POST' && request.path === '/somePath') {\n" +
                "    return {\n" +
                "        'statusCode': 200,\n" +
                "        'body': JSON.stringify({name: 'value'})\n" +
                "    };\n" +
                "} else {\n" +
                "    return {\n" +
                "        'statusCode': 406,\n" +
                "        'body': request.body\n" +
                "    };\n" +
                "}");
        HttpResponse httpResponse = new HttpResponseTemplateActionHandler().handle(template, request()
                .withPath("/somePath")
                .withMethod("POST")
                .withBody("some_body")
        );
        System.out.println("httpResponse = " + httpResponse);
        httpResponse = new HttpResponseTemplateActionHandler().handle(template, request()
                .withPath("/someOtherPath")
                .withBody("some_body")
        );
        System.out.println("httpResponse = " + httpResponse);
    }

    public HttpResponse handle(HttpTemplate httpTemplate, HttpRequest httpRequest) {
        HttpResponse httpResponse = notFoundResponse();

        try {
            if (jsonJavaScriptObject != null) {
                engine.eval("function handle(request) { var request = JSON.parse(request); " + httpTemplate.getTemplate() + "}");
                // HttpResponse handle(HttpRequest httpRequest) - ES5
                Object stringifiedResponse = jsonJavaScriptObject.callMember("stringify", ((Invocable) engine).invokeFunction("handle", httpRequest), null, 2);
                return objectMapper.readValue((String) stringifiedResponse, HttpResponseDTO.class).buildObject();
            } else {
                logger.error("JavaScript based templating is only available in a JVM with the \"nashorn\" JavaScript engine, " +
                        "please use a JVM with the \"nashorn\" JavaScript engine, such as Oracle Java 8+", new RuntimeException("\"nashorn\" JavaScript engine not available"));
            }
        } catch (Exception e) {
            logger.error("Exception forwarding request " + httpRequest, e);
        }

        httpTemplate.applyDelay();
        return httpResponse;
    }
}
