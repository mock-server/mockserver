package org.mockserver.mock.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.jtwig.environment.DefaultEnvironmentConfiguration;
import org.jtwig.environment.EnvironmentConfiguration;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpResponseJTwigTemplateActionHandler {
    private final static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final static Logger logger = LoggerFactory.getLogger(HttpResponseJTwigTemplateActionHandler.class);

    private final EnvironmentConfiguration configuration = new DefaultEnvironmentConfiguration();

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
        HttpResponse httpResponse = new HttpResponseJTwigTemplateActionHandler().handle(template, request()
                .withPath("/somePath")
                .withMethod("POST")
                .withBody("some_body")
        );
        System.out.println("httpResponse = " + httpResponse);
        httpResponse = new HttpResponseJTwigTemplateActionHandler().handle(template, request()
                .withPath("/someOtherPath")
                .withBody("some_body")
        );
        System.out.println("httpResponse = " + httpResponse);
    }

    public HttpResponse handle(HttpTemplate httpTemplate, HttpRequest httpRequest) {
        HttpResponse httpResponse = notFoundResponse();

        try {
            JtwigTemplate jtwigTemplate = JtwigTemplate.inlineTemplate("Hello {{ token }}!", configuration);
            JtwigModel model = JtwigModel.newModel().with("token", "World");
            return objectMapper.readValue(jtwigTemplate.render(model), HttpResponseDTO.class).buildObject();
        } catch (Exception e) {
            logger.error("Exception forwarding request " + httpRequest, e);
        }

        httpTemplate.applyDelay();
        return httpResponse;
    }
}
