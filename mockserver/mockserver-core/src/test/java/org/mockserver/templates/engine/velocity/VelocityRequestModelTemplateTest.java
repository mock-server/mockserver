package org.mockserver.templates.engine.velocity;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.log.model.LogEntry;
import org.slf4j.event.Level;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpResponseDTO;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.templates.engine.model.RequestModelFixtures.*;

public class VelocityRequestModelTemplateTest {

    private final VelocityTemplateEngine engine = new VelocityTemplateEngine(new MockServerLogger(VelocityRequestModelTemplateTest.class), configuration());

    @Test
    public void shouldRenderFormData() {
        String rendered = engine.renderTemplate("$!request.formData.name[0]|$!request.formData['tag'][1]|$!request.formData.size()", formRequest("name=Ada+Lovelace&tag=a&tag=b"));

        assertThat(rendered, is("Ada Lovelace|b|2"));
    }

    @Test
    public void shouldRenderMultipartParts() {
        String rendered = engine.renderTemplate(
            "$!request.parts.title[0].body|$!request.parts.file[0].filename|$!request.parts.file[0].contentType|$!request.parts.file[0].bodyAsBase64|$!request.parts.file[0].headers['Content-Type'][0]",
            multipartRequest()
        );

        assertThat(rendered, is("hello|notes.txt|text/plain|YWJj|text/plain"));
    }

    @Test
    public void shouldRenderAddressFieldsIdAndBodyAsBase64() {
        String rendered = engine.renderTemplate("$!request.host|$!request.port|$!request.scheme|$!request.baseUrl|$!request.id|$!request.bodyAsBase64", addressedRequest());

        assertThat(rendered, is("api.example.com|8443|https|https://api.example.com:8443|3f2a6c1e-request-id|YWJj"));
    }

    @Test
    public void shouldRenderEmptyForMalformedMultipartBody() {
        String rendered = engine.renderTemplate("[$!request.parts.size()]", multipartRequest("garbage".getBytes()));

        assertThat(rendered, is("[0]"));
    }

    @Test
    public void shouldNotDecodeTheBodyWhenTheTemplateDoesNotReadDerivedFields() {
        HttpRequest request = spy(multipartRequest());

        String rendered = engine.renderTemplate("$!request.method $!request.path $!request.body.length()", request);

        assertThat(rendered.startsWith("POST /upload "), is(true));
        verify(request, never()).getBodyAsRawBytes();
    }

    @Test
    public void shouldRenderTheDocumentedUploadExample() {
        HttpRequest request = multipartRequest().withHeader("Host", "files.example.com");
        request.withLogCorrelationId("req-1");

        HttpResponse response = engine.executeTemplate("{ 'statusCode': 201, 'headers': { 'Location': [ '$!request.baseUrl/uploads/$!request.parts.file[0].filename' ], 'X-Request-Id': [ '$!request.id' ] }, 'body': 'stored $!request.parts.file[0].filename titled $!request.parts.title[0].body' }", request, HttpResponseDTO.class);

        assertThat(response.getStatusCode(), is(201));
        assertThat(response.getFirstHeader("Location"), is("http://files.example.com/uploads/notes.txt"));
        assertThat(response.getFirstHeader("X-Request-Id"), is("req-1"));
        assertThat(response.getBodyAsString(), is("stored notes.txt titled hello"));
    }

    @Test
    public void shouldLogATemplateWarningWhenTheBodyIsOverTheDecodingCap() {
        MockServerLogger logger = mock(MockServerLogger.class);
        HttpRequest request = multipartRequest(multipartBodyWithFileOf(org.mockserver.templates.engine.model.HttpRequestTemplateObjectTestAccess.maxParsedBodyBytes()));

        String rendered = new VelocityTemplateEngine(logger, configuration()).renderTemplate("[$!request.parts.size()]", request);

        assertThat(rendered, is("[0]"));
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeastOnce()).logEvent(entries.capture());
        LogEntry warning = entries.getAllValues().stream().filter(entry -> entry.getLogLevel() == Level.WARN).findFirst().orElseThrow();
        assertThat(warning.getMessageFormat(), containsString("request.parts is empty because the request body is larger than"));
    }
}
