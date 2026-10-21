package org.mockserver.templates.engine.mustache;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpResponseDTO;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.templates.engine.model.RequestModelFixtures.*;

public class MustacheRequestModelTemplateTest {

    private final MustacheTemplateEngine engine = new MustacheTemplateEngine(new MockServerLogger(MustacheRequestModelTemplateTest.class), configuration());

    @Test
    public void shouldRenderFormData() {
        String rendered = engine.renderTemplate("{{ request.formData.name.0 }}|{{ request.formData.tag.1 }}|{{#request.formData.tag}}<{{.}}>{{/request.formData.tag}}", formRequest("name=Ada+Lovelace&tag=a&tag=b"));

        assertThat(rendered, is("Ada Lovelace|b|<a><b>"));
    }

    @Test
    public void shouldRenderMultipartParts() {
        String rendered = engine.renderTemplate(
            "{{ request.parts.title.0.body }}|{{#request.parts.file}}{{ filename }}:{{ contentType }}:{{ bodyAsBase64 }}{{/request.parts.file}}",
            multipartRequest()
        );

        assertThat(rendered, is("hello|notes.txt:text/plain:YWJj"));
    }

    @Test
    public void shouldRenderAddressFieldsIdAndBodyAsBase64() {
        String rendered = engine.renderTemplate("{{ request.host }}|{{ request.port }}|{{ request.scheme }}|{{{ request.baseUrl }}}|{{ request.id }}|{{ request.bodyAsBase64 }}", addressedRequest());

        assertThat(rendered, is("api.example.com|8443|https|https://api.example.com:8443|3f2a6c1e-request-id|YWJj"));
    }

    @Test
    public void shouldRenderEmptyForMalformedMultipartBody() {
        String rendered = engine.renderTemplate("[{{#request.parts.file}}x{{/request.parts.file}}]", multipartRequest("garbage".getBytes()));

        assertThat(rendered, is("[]"));
    }

    @Test
    public void shouldNotDecodeTheBodyWhenTheTemplateDoesNotReadDerivedFields() {
        HttpRequest request = spy(multipartRequest());

        String rendered = engine.renderTemplate("{{ request.method }} {{ request.path }}", request);

        assertThat(rendered, is("POST /upload"));
        verify(request, never()).getBodyAsRawBytes();
    }

    @Test
    public void shouldRenderTheDocumentedUploadExample() {
        HttpRequest request = multipartRequest().withHeader("Host", "files.example.com");
        request.withLogCorrelationId("req-1");

        HttpResponse response = engine.executeTemplate("{ 'statusCode': 201, 'headers': { 'Location': [ '{{ request.baseUrl }}/uploads/{{ request.parts.file.0.filename }}' ], 'X-Request-Id': [ '{{ request.id }}' ] }, 'body': 'stored {{ request.parts.file.0.filename }} titled {{ request.parts.title.0.body }}' }", request, HttpResponseDTO.class);

        assertThat(response.getStatusCode(), is(201));
        assertThat(response.getFirstHeader("Location"), is("http://files.example.com/uploads/notes.txt"));
        assertThat(response.getFirstHeader("X-Request-Id"), is("req-1"));
        assertThat(response.getBodyAsString(), is("stored notes.txt titled hello"));
    }
}
