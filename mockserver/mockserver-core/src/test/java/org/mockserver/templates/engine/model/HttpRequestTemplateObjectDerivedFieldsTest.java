package org.mockserver.templates.engine.model;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.codec.MultipartFormDataDecoder.DecodedPart;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.SegmentedBytes;
import org.mockserver.model.X509Certificate;
import org.slf4j.event.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.X509Certificate.x509Certificate;
import static org.mockserver.templates.engine.model.RequestModelFixtures.*;

public class HttpRequestTemplateObjectDerivedFieldsTest {

    @Test
    public void shouldDeriveHostPortSchemeAndBaseUrlFromHostHeader() {
        HttpRequestTemplateObject templateObject = new HttpRequestTemplateObject(addressedRequest());

        assertThat(templateObject.getHost(), is("api.example.com"));
        assertThat(templateObject.getPort(), is(8443));
        assertThat(templateObject.getScheme(), is("https"));
        assertThat(templateObject.getBaseUrl(), is("https://api.example.com:8443"));
    }

    @Test
    public void shouldUseSchemeDefaultPortWhenHostHeaderHasNoPort() {
        HttpRequestTemplateObject secure = new HttpRequestTemplateObject(request().withHeader("host", "example.com").withSecure(true));
        HttpRequestTemplateObject plain = new HttpRequestTemplateObject(request().withHeader("HOST", "example.com"));

        assertThat(secure.getPort(), is(443));
        assertThat(secure.getBaseUrl(), is("https://example.com"));
        assertThat(plain.getPort(), is(80));
        assertThat(plain.getScheme(), is("http"));
        assertThat(plain.getBaseUrl(), is("http://example.com"));
    }

    @Test
    public void shouldDeriveHostAndPortFromBracketedIpv6HostHeader() {
        HttpRequestTemplateObject templateObject = new HttpRequestTemplateObject(request().withHeader("Host", "[::1]:1080"));

        assertThat(templateObject.getHost(), is("::1"));
        assertThat(templateObject.getPort(), is(1080));
        assertThat(templateObject.getBaseUrl(), is("http://[::1]:1080"));
    }

    @Test
    public void shouldFallBackToLocalAddressWhenThereIsNoHostHeader() {
        HttpRequestTemplateObject ipv4 = new HttpRequestTemplateObject(request().withLocalAddress("127.0.0.1:1080"));
        HttpRequestTemplateObject resolved = new HttpRequestTemplateObject(request().withLocalAddress("localhost/127.0.0.1:1090"));
        HttpRequestTemplateObject ipv6 = new HttpRequestTemplateObject(request().withLocalAddress("[0:0:0:0:0:0:0:1]:1080").withSecure(true));

        assertThat(ipv4.getHost(), is("127.0.0.1"));
        assertThat(ipv4.getPort(), is(1080));
        assertThat(ipv4.getBaseUrl(), is("http://127.0.0.1:1080"));
        assertThat(resolved.getHost(), is("127.0.0.1"));
        assertThat(resolved.getPort(), is(1090));
        assertThat(ipv6.getHost(), is("0:0:0:0:0:0:0:1"));
        assertThat(ipv6.getBaseUrl(), is("https://[0:0:0:0:0:0:0:1]:1080"));
    }

    @Test
    public void shouldReturnNullAddressFieldsWhenNothingIdentifiesTheHost() {
        HttpRequestTemplateObject templateObject = new HttpRequestTemplateObject(request());

        assertThat(templateObject.getHost(), is(nullValue()));
        assertThat(templateObject.getPort(), is(nullValue()));
        assertThat(templateObject.getBaseUrl(), is(nullValue()));
        assertThat(templateObject.getScheme(), is("http"));
    }

    @Test
    public void shouldIgnoreANonNumericHostHeaderPort() {
        HttpRequestTemplateObject templateObject = new HttpRequestTemplateObject(request().withHeader("Host", "example.com:notaport"));

        assertThat(templateObject.getHost(), is("example.com"));
        assertThat(templateObject.getPort(), is(nullValue()));
    }

    @Test
    public void shouldExposeTheLogCorrelationIdAsTheRequestId() {
        assertThat(new HttpRequestTemplateObject(addressedRequest()).getId(), is("3f2a6c1e-request-id"));
        assertThat(new HttpRequestTemplateObject(request()).getId(), is(nullValue()));
    }

    @Test
    public void shouldEncodeTheBodyAsBase64() {
        assertThat(new HttpRequestTemplateObject(addressedRequest()).getBodyAsBase64(), is("YWJj"));
        assertThat(new HttpRequestTemplateObject(request().withBody(new byte[]{0, (byte) 0xff, 0x10})).getBodyAsBase64(), is("AP8Q"));
        assertThat(new HttpRequestTemplateObject(request()).getBodyAsBase64(), is(""));
        assertThat(new HttpRequestTemplateObject(null).getBodyAsBase64(), is(""));
    }

    @Test
    public void shouldDecodeUrlEncodedFormData() {
        Map<String, List<String>> formData = new HttpRequestTemplateObject(formRequest("name=Ada+Lovelace&tag=a&tag=b%26c&empty=")).getFormData();

        assertThat(formData.get("name"), is(Collections.singletonList("Ada Lovelace")));
        assertThat(formData.get("tag"), is(Arrays.asList("a", "b&c")));
        assertThat(formData.get("empty"), is(Collections.singletonList("")));
        assertThat(formData.keySet(), contains("name", "tag", "empty"));
    }

    @Test
    public void shouldDecodeFormDataFromAParameterBody() {
        Map<String, List<String>> formData = new HttpRequestTemplateObject(request().withBody(params(param("name", "one", "two")))).getFormData();

        assertThat(formData.get("name"), is(Arrays.asList("one", "two")));
    }

    @Test
    public void shouldDecodeFormDataInTheDeclaredCharset() {
        HttpRequest request = request()
            .withHeader("Content-Type", "application/x-www-form-urlencoded; charset=ISO-8859-1")
            .withBody("city=M%FCnchen".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(new HttpRequestTemplateObject(request).getFormData().get("city"), is(Collections.singletonList("München")));
    }

    @Test
    public void shouldGiveEmptyFormDataForOtherContentTypesAndMalformedBodies() {
        assertThat(new HttpRequestTemplateObject(request().withHeader("Content-Type", "application/json").withBody("a=b")).getFormData().isEmpty(), is(true));
        assertThat(new HttpRequestTemplateObject(request().withBody("a=b")).getFormData().isEmpty(), is(true));
        assertThat(new HttpRequestTemplateObject(formRequest("a=%zz&b=%")).getFormData().isEmpty(), is(true));
        assertThat(new HttpRequestTemplateObject(null).getFormData().isEmpty(), is(true));
    }

    @Test
    public void shouldBoundTheNumberOfDecodedFormFields() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < HttpRequestTemplateObject.MAX_FORM_FIELDS + 50; i++) {
            body.append("f").append(i).append("=v&");
        }

        assertThat(new HttpRequestTemplateObject(formRequest(body.toString())).getFormData().size(), is(HttpRequestTemplateObject.MAX_FORM_FIELDS));
    }

    @Test
    public void shouldDecodeMultipartParts() {
        Map<String, List<MultipartPartTemplateObject>> parts = new HttpRequestTemplateObject(multipartRequest()).getParts();

        assertThat(parts.keySet(), contains("title", "file"));
        MultipartPartTemplateObject title = parts.get("title").get(0);
        assertThat(title.getName(), is("title"));
        assertThat(title.getBody(), is("hello"));
        assertThat(title.getFilename(), is(nullValue()));
        assertThat(title.getHeaders().get("content-disposition"), is(Collections.singletonList("form-data; name=\"title\"")));
        MultipartPartTemplateObject file = parts.get("file").get(0);
        assertThat(file.getFilename(), is("notes.txt"));
        assertThat(file.getContentType(), is("text/plain"));
        assertThat(file.getBody(), is("abc"));
        assertThat(file.getBodyAsBase64(), is("YWJj"));
        assertThat(file.getHeaders().get("Content-Type"), is(Collections.singletonList("text/plain")));
        assertThat(file.getHeaders().get("Content-Disposition"), is(Collections.singletonList("form-data; name=\"file\"; filename=\"notes.txt\"")));
    }

    @Test
    public void shouldGiveEmptyPartsForMalformedOrOversizedMultipartBodies() {
        assertThat(new HttpRequestTemplateObject(multipartRequest("not a multipart body".getBytes(StandardCharsets.UTF_8))).getParts().isEmpty(), is(true));
        assertThat(new HttpRequestTemplateObject(multipartRequest(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"x\"\r\n\r\nunterminated").getBytes(StandardCharsets.UTF_8))).getParts().isEmpty(), is(true));
        assertThat(new HttpRequestTemplateObject(request().withHeader("Content-Type", "multipart/form-data").withBody("--x\r\n")).getParts().isEmpty(), is(true));
        assertThat(new HttpRequestTemplateObject(formRequest("a=b")).getParts().isEmpty(), is(true));
        assertThat(new HttpRequestTemplateObject(null).getParts().isEmpty(), is(true));
    }

    @Test
    public void shouldBoundTheNumberOfDecodedParts() {
        assertThat(new HttpRequestTemplateObject(multipartRequestWithParts(HttpRequestTemplateObject.MAX_MULTIPART_PARTS)).getParts().size(), is(HttpRequestTemplateObject.MAX_MULTIPART_PARTS));
        assertThat(new HttpRequestTemplateObject(multipartRequestWithParts(HttpRequestTemplateObject.MAX_MULTIPART_PARTS + 1)).getParts().isEmpty(), is(true));
    }

    @Test
    public void shouldNotReadTheBodyBytesUntilADerivedFieldIsRead() {
        HttpRequest request = spy(multipartRequest());

        HttpRequestTemplateObject templateObject = new HttpRequestTemplateObject(request);
        templateObject.getMethod();
        templateObject.getHeaders();
        templateObject.getBody();
        templateObject.toString();
        verify(request, never()).getBodyAsRawBytes();

        templateObject.getParts();
        templateObject.getParts();
        verify(request, times(1)).getBodyAsRawBytes();
    }

    @Test
    public void shouldLeaveTheJsonFormUnchanged() {
        String json = new HttpRequestTemplateObject(addressedRequest()).toString();

        for (String derived : Arrays.asList("formData", "parts", "\"host\"", "\"port\"", "scheme", "baseUrl", "bodyAsBase64", "\"id\"")) {
            assertThat(json, not(containsString(derived)));
        }
    }

    @Test
    public void shouldReadPlainSourceValuesThroughTheSourceRequest() {
        List<X509Certificate> chain = Collections.singletonList(x509Certificate().withSerialNumber("123"));
        HttpRequest request = request()
            .withLocalAddress("127.0.0.1:1080")
            .withRemoteAddress("10.0.0.2:51234")
            .withKeepAlive(true)
            .withClientCertificateChain(chain);
        HttpRequestTemplateObject templateObject = new HttpRequestTemplateObject(request);
        HttpRequestTemplateObject withoutSource = new HttpRequestTemplateObject(null);

        assertThat(templateObject.getLocalAddress(), is("127.0.0.1:1080"));
        assertThat(templateObject.getRemoteAddress(), is("10.0.0.2:51234"));
        assertThat(templateObject.getKeepAlive(), is(true));
        assertThat(templateObject.getClientCertificateChain(), is(chain));
        assertThat(templateObject.toString(), allOf(containsString("\"localAddress\" : \"127.0.0.1:1080\""), containsString("\"remoteAddress\" : \"10.0.0.2:51234\""), containsString("\"keepAlive\" : true")));
        assertThat(templateObject, is(new HttpRequestTemplateObject(request)));
        assertThat(templateObject, not(new HttpRequestTemplateObject(request.clone().withRemoteAddress("10.0.0.3:1"))));
        assertThat(withoutSource.getLocalAddress(), is(nullValue()));
        assertThat(withoutSource.getRemoteAddress(), is(nullValue()));
        assertThat(withoutSource.getKeepAlive(), is(nullValue()));
        assertThat(withoutSource.getClientCertificateChain(), is(nullValue()));
    }

    @Test
    public void shouldNotGrowTheTemplateObjectEveryRenderAllocates() {
        // every template render allocates one of these; 17 references and one int is the size it had before the
        // derived fields were added (104 bytes with compressed oops), so holding the source must not add fields
        int referenceFields = 0;
        int primitiveFields = 0;
        for (Field field : HttpRequestTemplateObject.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                if (field.getType().isPrimitive()) {
                    primitiveFields++;
                } else {
                    referenceFields++;
                }
            }
        }

        assertThat(referenceFields, lessThanOrEqualTo(17));
        assertThat(primitiveFields, lessThanOrEqualTo(1));
    }

    @Test
    public void shouldServeDerivedFieldsAsJson() {
        HttpRequestTemplateObject templateObject = new HttpRequestTemplateObject(multipartRequest().withHeader("Host", "example.com"));

        assertThat(templateObject.derivedFieldAsJson("host"), is("\"example.com\""));
        assertThat(templateObject.derivedFieldAsJson("port"), is("80"));
        assertThat(templateObject.derivedFieldAsJson("parts"), allOf(containsString("\"filename\":\"notes.txt\""), not(containsString("\"body"))));
        assertThat(templateObject.derivedFieldAsJson("unknown"), is("null"));
        assertThat(templateObject.derivedFieldAsJson(null), is("null"));
    }

    @Test
    public void shouldDecodeAWellFormedMultipartBodyJustUnderTheCap() {
        byte[] body = multipartBodyWithFileOf(HttpRequestTemplateObject.MAX_PARSED_BODY_BYTES - 1024);

        Map<String, List<MultipartPartTemplateObject>> parts = new HttpRequestTemplateObject(multipartRequest(body)).getParts();

        assertThat(parts.get("file").get(0).getFilename(), is("big.bin"));
    }

    @Test
    public void shouldSkipAndWarnForAWellFormedMultipartBodyOverTheCap() {
        MockServerLogger logger = mock(MockServerLogger.class);
        byte[] body = multipartBodyWithFileOf(HttpRequestTemplateObject.MAX_PARSED_BODY_BYTES);

        assertThat(new HttpRequestTemplateObject(multipartRequest(body), logger).getParts().isEmpty(), is(true));
        assertThat(warning(logger), is("request.parts is empty because the request body is larger than{}bytes, the most a template decodes [" + HttpRequestTemplateObject.MAX_PARSED_BODY_BYTES + "]"));
    }

    @Test
    public void shouldSkipAndWarnForAWellFormedFormBodyOverTheCap() {
        MockServerLogger logger = mock(MockServerLogger.class);
        byte[] body = new byte[HttpRequestTemplateObject.MAX_PARSED_BODY_BYTES + 1];
        Arrays.fill(body, (byte) 'x');
        body[0] = 'a';
        body[1] = '=';

        assertThat(new HttpRequestTemplateObject(request().withHeader("Content-Type", "application/x-www-form-urlencoded").withBody(body), logger).getFormData().isEmpty(), is(true));
        assertThat(warning(logger), is("request.formData is empty because the request body is larger than{}bytes, the most a template decodes [" + HttpRequestTemplateObject.MAX_PARSED_BODY_BYTES + "]"));
        assertThat(new HttpRequestTemplateObject(request().withHeader("Content-Type", "application/x-www-form-urlencoded").withBody(Arrays.copyOf(body, HttpRequestTemplateObject.MAX_PARSED_BODY_BYTES))).getFormData().get("a").get(0).length(), is(HttpRequestTemplateObject.MAX_PARSED_BODY_BYTES - 2));
    }

    @Test
    public void shouldCheckASegmentedBodySizeBeforeJoiningIt() {
        SegmentedBytes segments = new SegmentedBytes();
        byte[] chunk = new byte[1024 * 1024];
        for (int i = 0; i <= HttpRequestTemplateObject.MAX_PARSED_BODY_BYTES / chunk.length; i++) {
            segments.write(chunk, 0, chunk.length);
        }
        HttpRequest request = spy(request().withHeader("Content-Type", "multipart/form-data; boundary=" + BOUNDARY).withBody(BinaryBody.fromSegmentedBytes(segments, null)));

        assertThat(new HttpRequestTemplateObject(request).getParts().isEmpty(), is(true));
        verify(request, never()).getBodyAsRawBytes();
    }

    @Test
    public void shouldWarnWhenThereAreTooManyPartsOrFormFields() {
        MockServerLogger partsLogger = mock(MockServerLogger.class);
        new HttpRequestTemplateObject(multipartRequestWithParts(HttpRequestTemplateObject.MAX_MULTIPART_PARTS + 1), partsLogger).getParts();
        assertThat(warning(partsLogger), is("request.parts is empty because the request body has more than{}parts [" + HttpRequestTemplateObject.MAX_MULTIPART_PARTS + "]"));

        MockServerLogger formLogger = mock(MockServerLogger.class);
        StringBuilder form = new StringBuilder();
        for (int i = 0; i <= HttpRequestTemplateObject.MAX_FORM_FIELDS; i++) {
            form.append("f").append(i).append("=v&");
        }
        new HttpRequestTemplateObject(formRequest(form.toString()), formLogger).getFormData();
        assertThat(warning(formLogger), is("request.formData read only the first{}fields of the request body [" + HttpRequestTemplateObject.MAX_FORM_FIELDS + "]"));

        MockServerLogger quietLogger = mock(MockServerLogger.class);
        new HttpRequestTemplateObject(multipartRequest(), quietLogger).getParts();
        new HttpRequestTemplateObject(formRequest("a=b"), quietLogger).getFormData();
        verify(quietLogger, never()).logEvent(org.mockito.ArgumentMatchers.any(LogEntry.class));
    }

    @Test
    public void shouldNotCopyAPartsContentUntilItsBodyIsRead() {
        DecodedPart decoded = spy(new DecodedPart("file", "notes.txt", "text/plain", null, "abc".getBytes(StandardCharsets.UTF_8)));
        MultipartPartTemplateObject part = new MultipartPartTemplateObject(decoded);

        part.getName();
        part.getFilename();
        part.getContentType();
        part.getHeaders();
        part.describeWithoutBody();
        verify(decoded, never()).getBytes();

        assertThat(part.getBody(), is("abc"));
        verify(decoded, times(1)).getBytes();
    }

    @Test
    public void shouldServePartsToJavaScriptWithoutTheirContent() {
        HttpRequestTemplateObject templateObject = new HttpRequestTemplateObject(multipartRequest(multipartBodyWithFileOf(1024 * 1024)));

        String partsJson = templateObject.derivedFieldAsJson("parts");

        assertThat(partsJson.length(), lessThan(500));
        assertThat(partsJson, allOf(containsString("\"filename\":\"big.bin\""), not(containsString("\"body"))));
        assertThat(templateObject.partField("file", 0, "body").length(), is(1024 * 1024));
        assertThat(templateObject.partField("file", 0, "bodyAsBase64").length(), is(4 * ((1024 * 1024 + 2) / 3)));
        assertThat(templateObject.partField("file", 1, "body"), is(nullValue()));
        assertThat(templateObject.partField("missing", 0, "body"), is(nullValue()));
        assertThat(templateObject.partField("file", 0, "other"), is(nullValue()));
    }

    @Test
    public void shouldEscapeQuotesInTheReconstructedContentDisposition() {
        MultipartPartTemplateObject part = new MultipartPartTemplateObject(new DecodedPart("a\"b", "c\\d.txt", null, null, new byte[0]));

        assertThat(part.getHeaders().get("Content-Disposition").get(0), is("form-data; name=\"a\\\"b\"; filename=\"c\\\\d.txt\""));
    }

    private static String warning(MockServerLogger logger) {
        ArgumentCaptor<LogEntry> entry = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger).logEvent(entry.capture());
        assertThat(entry.getValue().getLogLevel(), is(Level.WARN));
        return entry.getValue().getMessageFormat() + " " + Arrays.toString(entry.getValue().getArguments());
    }
}
