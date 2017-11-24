package org.mockserver.mock.action;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateActionHandler {

    private JavaScriptTemplateEngine javaScriptTemplateEngine = new JavaScriptTemplateEngine();
    private VelocityTemplateEngine velocityTemplateEngine = new VelocityTemplateEngine();

    public static void main(String[] args) {
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "if (request.method === 'POST' && request.path === '/somePath') {\n" +
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
        template = template(HttpTemplate.TemplateType.VELOCITY, "#if ( $request.method.value == \"POST\" )\n" +
                "    {\n" +
                "        'statusCode': 200,\n" +
                "        'body': \"{'name': 'value'}\"\n" +
                "    }\n" +
                "#else\n" +
                "    {\n" +
                "        'statusCode': 406,\n" +
                "        'body': $request.body\n" +
                "    }\n" +
                "#end");
        httpResponse = new HttpResponseTemplateActionHandler().handle(template, request()
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

        TemplateEngine templateEngine = null;
        switch (httpTemplate.getTemplateType()) {
            case VELOCITY:
                templateEngine = velocityTemplateEngine;
                break;
            case JAVASCRIPT:
                templateEngine = javaScriptTemplateEngine;
                break;
        }
        if (templateEngine != null) {
            HttpResponse stringifiedResponse = templateEngine.executeTemplate(httpTemplate.getTemplate(), httpRequest);
            if (stringifiedResponse != null) {
                return stringifiedResponse;
            }
        }

        httpTemplate.applyDelay();
        return httpResponse;
    }

}
