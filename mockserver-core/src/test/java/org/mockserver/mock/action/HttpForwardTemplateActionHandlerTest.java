package org.mockserver.mock.action;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;

import javax.script.ScriptEngineManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpForwardTemplateActionHandlerTest {

    @Mock
    private NettyHttpClient mockHttpClient;
    @InjectMocks
    private HttpForwardTemplateActionHandler httpForwardTemplateActionHandler;

    private LoggingFormatter mockLogFormatter;

    @Before
    public void setupMocks() {
        mockLogFormatter = mock(LoggingFormatter.class);
        httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler(mockLogFormatter);
        initMocks(this);
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateFirstExample() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.JAVASCRIPT, "return { 'path': \"somePath\", 'body': JSON.stringify({name: 'value'}) };");
        HttpRequest httpRequest = request("somePath").withBody("{\"name\":\"value\"}");
        HttpResponse httpResponse = response("some_body");
        when(mockHttpClient.sendRequest(httpRequest)).thenReturn(httpResponse);

        // when
        HttpResponse actualHttpResponse = httpForwardTemplateActionHandler.handle(template, request()
                .withPath("/somePath")
                .withMethod("POST")
                .withBody("some_body")
        );

        // then
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            verify(mockHttpClient).sendRequest(httpRequest);
            assertThat(actualHttpResponse, is(httpResponse));
        } else {
            assertThat(actualHttpResponse, is(notFoundResponse()));
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateFirstExample() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "{" + NEW_LINE +
                "    'path': \"$!request.path\"," + NEW_LINE +
                "    'method': \"$!request.method\"," + NEW_LINE +
                "    'body': \"$!request.body\"" + NEW_LINE +
                "}");
        HttpRequest httpRequest = request("/somePath").withMethod("POST").withBody("some_body");
        HttpResponse httpResponse = response("some_body");
        when(mockHttpClient.sendRequest(httpRequest)).thenReturn(httpResponse);

        // when
        HttpResponse actualHttpResponse = httpForwardTemplateActionHandler.handle(template, httpRequest);

        // then
        verify(mockHttpClient).sendRequest(httpRequest);
        assertThat(actualHttpResponse, is(httpResponse));
    }

}
