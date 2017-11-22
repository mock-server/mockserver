package org.mockserver.mock.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.script.VelocityScriptEngine;
import org.apache.velocity.script.VelocityScriptEngineFactory;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.jtwig.environment.DefaultEnvironmentConfiguration;
import org.jtwig.environment.EnvironmentConfiguration;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.StringWriter;
import java.io.Writer;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpResponseVelocityTemplateActionHandler {
    private final static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final static Logger logger = LoggerFactory.getLogger(HttpResponseVelocityTemplateActionHandler.class);

    private final ScriptEngineManager manager = new ScriptEngineManager();
    private final ScriptEngine engine = manager.getEngineByName("velocity");

    public HttpResponseVelocityTemplateActionHandler() {
        manager.registerEngineName("velocity", new VelocityScriptEngineFactory());
//            System.setProperty(VelocityScriptEngine.VELOCITY_PROPERTIES_KEY, "path/to/velocity.properties");
    }

    public static void main(String[] args) {
//        HttpTemplate template = template("#if ( $request.method == \"POST\" && $request.path == \"/somePath\" ) {\n" +
        HttpTemplate template = template("#if ( $request.method.value == \"POST\" )\n" +
                "    {\n" +
                "        'statusCode': 200,\n" +
                "        'body': \"{'name': 'value'}\"\n" +
                "    }\n" +
                "#else\n" +
                "    {\n" +
                "        'statusCode': 406,\n" +
                "        'body': $request.method\n" +
//                "        'body': $request.body\n" +
                "    }\n" +
                "#end");
        HttpResponse httpResponse = new HttpResponseVelocityTemplateActionHandler().handle(template, request()
                .withPath("/somePath")
                .withMethod("POST")
                .withBody("some_body")
        );
        System.out.println("httpResponse = " + httpResponse);
        httpResponse = new HttpResponseVelocityTemplateActionHandler().handle(template, request()
                .withPath("/someOtherPath")
                .withBody("some_body")
        );
        System.out.println("httpResponse = " + httpResponse);
    }

    public HttpResponse handle(HttpTemplate httpTemplate, HttpRequest httpRequest) {
        HttpResponse httpResponse = notFoundResponse();

        try {
            Writer writer = new StringWriter();
            ScriptContext context = engine.getContext();
            context.setWriter(writer);
            context.setAttribute("request", new HttpRequestDTO(httpRequest), ScriptContext.ENGINE_SCOPE);
            engine.eval(httpTemplate.getTemplate());
            return objectMapper.readValue(writer.toString(), HttpResponseDTO.class).buildObject();
        } catch (Exception e) {
            logger.error("Exception forwarding request " + httpRequest, e);
        }

        httpTemplate.applyDelay();
        return httpResponse;
    }
}
