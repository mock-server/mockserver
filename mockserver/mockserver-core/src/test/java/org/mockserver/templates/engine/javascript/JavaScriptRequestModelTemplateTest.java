package org.mockserver.templates.engine.javascript;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpResponseDTO;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.templates.engine.model.RequestModelFixtures.*;

public class JavaScriptRequestModelTemplateTest {

    private JavaScriptTemplateEngine engine;

    @Before
    public void createEngine() {
        assumeThat("GraalVM Polyglot API available", JavaScriptTemplateEngine.isPolyglotAvailable(), is(true));
        engine = new JavaScriptTemplateEngine(new MockServerLogger(JavaScriptRequestModelTemplateTest.class), configuration().javascriptTemplateExecutionTimeout(0L));
    }

    @Test
    public void shouldRenderFormData() {
        String rendered = engine.renderTemplateText("return request.formData.name[0] + '|' + request.formData['tag'].join(',') + '|' + Object.keys(request.formData).length;", formRequest("name=Ada+Lovelace&tag=a&tag=b"));

        assertThat(rendered, is("Ada Lovelace|a,b|2"));
    }

    @Test
    public void shouldRenderMultipartParts() {
        String rendered = engine.renderTemplateText(
            "var file = request.parts.file[0]; return request.parts.title[0].body + '|' + file.filename + '|' + file.contentType + '|' + file.bodyAsBase64 + '|' + file.headers['Content-Type'][0];",
            multipartRequest()
        );

        assertThat(rendered, is("hello|notes.txt|text/plain|YWJj|text/plain"));
    }

    @Test
    public void shouldRenderAddressFieldsIdAndBodyAsBase64() {
        String rendered = engine.renderTemplateText("return [request.host, request.port, request.scheme, request.baseUrl, request.id, request.bodyAsBase64].join('|');", addressedRequest());

        assertThat(rendered, is("api.example.com|8443|https|https://api.example.com:8443|3f2a6c1e-request-id|YWJj"));
    }

    @Test
    public void shouldUseDerivedFieldsInAResponseTemplate() {
        HttpResponse response = engine.executeTemplate(
            "return { statusCode: 201, headers: { Location: [ request.baseUrl + '/uploads/' + request.parts.file[0].filename ] }, body: request.formData.toString() };",
            multipartRequest().withHeader("Host", "files.example.com"),
            HttpResponseDTO.class
        );

        assertThat(response.getStatusCode(), is(201));
        assertThat(response.getFirstHeader("Location"), is("http://files.example.com/uploads/notes.txt"));
    }

    @Test
    public void shouldRenderEmptyForMalformedMultipartBody() {
        String rendered = engine.renderTemplateText("return '[' + Object.keys(request.parts).length + ']';", multipartRequest("garbage".getBytes()));

        assertThat(rendered, is("[0]"));
    }

    @Test
    public void shouldKeepDerivedFieldsOutOfTheEnumeratedRequest() {
        String rendered = engine.renderTemplateText("var keys = Object.keys(request); return ['formData', 'parts', 'host', 'port', 'scheme', 'baseUrl', 'bodyAsBase64', 'id'].filter(function (k) { return keys.indexOf(k) >= 0 || JSON.stringify(request).indexOf('\"' + k + '\"') >= 0; }).length + '|' + request.scheme;", addressedRequest());

        assertThat(rendered, is("0|https"));
    }

    @Test
    public void shouldLetATemplateOverwriteADerivedField() {
        String rendered = engine.renderTemplateText("request.host = 'replaced'; return request.host;", addressedRequest());

        assertThat(rendered, is("replaced"));
    }

    @Test
    public void shouldNotDecodeTheBodyWhenTheTemplateDoesNotReadDerivedFields() {
        HttpRequest request = spy(multipartRequest());

        String rendered = engine.renderTemplateText("return request.method + ' ' + request.path;", request);

        assertThat(rendered, is("POST /upload"));
        verify(request, never()).getBodyAsRawBytes();
    }

    @Test
    public void shouldRenderTheDocumentedUploadExample() {
        HttpRequest request = multipartRequest().withHeader("Host", "files.example.com");
        request.withLogCorrelationId("req-1");

        HttpResponse response = engine.executeTemplate("return { 'statusCode': 201, 'headers': { 'Location': [ request.baseUrl + '/uploads/' + request.parts.file[0].filename ], 'X-Request-Id': [ request.id ] }, 'body': 'stored ' + request.parts.file[0].filename + ' titled ' + request.parts.title[0].body };", request, HttpResponseDTO.class);

        assertThat(response.getStatusCode(), is(201));
        assertThat(response.getFirstHeader("Location"), is("http://files.example.com/uploads/notes.txt"));
        assertThat(response.getFirstHeader("X-Request-Id"), is("req-1"));
        assertThat(response.getBodyAsString(), is("stored notes.txt titled hello"));
    }

    @Test
    public void shouldNotFetchAPartsContentWhenOnlyItsFilenameIsRead() {
        String rendered = engine.renderTemplateText(
            "var part = request.parts.file[0];"
                + " var before = typeof Object.getOwnPropertyDescriptor(part, 'body').get + ',' + typeof Object.getOwnPropertyDescriptor(part, 'bodyAsBase64').get;"
                + " var filename = part.filename;"
                + " var body = part.body;"
                + " var after = typeof Object.getOwnPropertyDescriptor(part, 'body').get + ',' + typeof Object.getOwnPropertyDescriptor(part, 'bodyAsBase64').get;"
                + " return filename + '|' + before + '|' + body + '|' + after + '|' + Object.keys(part).join(',');",
            multipartRequest()
        );

        assertThat(rendered, is("notes.txt|function,function|abc|undefined,function|name,filename,contentType,headers,body,bodyAsBase64"));
    }
}
