package org.mockserver.imports;

import org.junit.Test;
import org.mockserver.fixture.FixtureRedactor;
import org.mockserver.mock.Expectation;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Body;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.XPathBody;
import org.mockserver.model.XmlBody;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

/**
 * Behavioural tests for WS4.2 "Redaction at import": importing captured HTTP
 * traffic (HAR / Postman) must mask sensitive headers and body fields before the
 * expectation is persisted, on by default, with an opt-out.
 */
public class ImportRedactionTest {

    private static final String PLACEHOLDER = FixtureRedactor.REDACTED_PLACEHOLDER;

    private final HarImporter harImporter = new HarImporter();
    private final PostmanCollectionImporter postmanImporter = new PostmanCollectionImporter();

    // A HAR entry whose request carries an Authorization header and a body with an
    // api_key field, a non-sensitive X-Tenant header, and a response that sets a
    // Set-Cookie header and returns an access_token body field.
    private static final String SENSITIVE_HAR = "{\n" +
        "  \"log\": {\n" +
        "    \"entries\": [\n" +
        "      {\n" +
        "        \"request\": {\n" +
        "          \"method\": \"POST\",\n" +
        "          \"url\": \"https://api.example.com/login\",\n" +
        "          \"headers\": [\n" +
        "            { \"name\": \"Authorization\", \"value\": \"Bearer super-secret-token\" },\n" +
        "            { \"name\": \"X-Api-Key\", \"value\": \"req-secret-key\" },\n" +
        "            { \"name\": \"X-Tenant\", \"value\": \"acme\" }\n" +
        "          ],\n" +
        "          \"postData\": {\n" +
        "            \"text\": \"{\\\"user\\\":\\\"alice\\\",\\\"api_key\\\":\\\"sk-live-12345\\\"}\"\n" +
        "          }\n" +
        "        },\n" +
        "        \"response\": {\n" +
        "          \"status\": 200,\n" +
        "          \"headers\": [\n" +
        "            { \"name\": \"X-Api-Key\", \"value\": \"resp-secret-key\" },\n" +
        "            { \"name\": \"Content-Type\", \"value\": \"application/json\" }\n" +
        "          ],\n" +
        "          \"content\": {\n" +
        "            \"text\": \"{\\\"access_token\\\":\\\"at-secret-999\\\",\\\"expires_in\\\":3600}\"\n" +
        "          }\n" +
        "        }\n" +
        "      }\n" +
        "    ]\n" +
        "  }\n" +
        "}";

    // A Postman collection whose request carries an Authorization header and an
    // api_key body field, a non-sensitive X-Tenant header, and an example response
    // with a Set-Cookie header and an access_token body field.
    private static final String SENSITIVE_POSTMAN = "{\n" +
        "  \"info\": { \"name\": \"Auth API\" },\n" +
        "  \"item\": [\n" +
        "    {\n" +
        "      \"name\": \"Login\",\n" +
        "      \"request\": {\n" +
        "        \"method\": \"POST\",\n" +
        "        \"url\": \"http://api.example.com/login\",\n" +
        "        \"header\": [\n" +
        "          { \"key\": \"Authorization\", \"value\": \"Bearer super-secret-token\" },\n" +
        "          { \"key\": \"X-Tenant\", \"value\": \"acme\" }\n" +
        "        ],\n" +
        "        \"body\": {\n" +
        "          \"mode\": \"raw\",\n" +
        "          \"raw\": \"{\\\"user\\\":\\\"alice\\\",\\\"api_key\\\":\\\"sk-live-12345\\\"}\"\n" +
        "        }\n" +
        "      },\n" +
        "      \"response\": [\n" +
        "        {\n" +
        "          \"name\": \"200 OK\",\n" +
        "          \"code\": 200,\n" +
        "          \"header\": [\n" +
        "            { \"key\": \"Set-Cookie\", \"value\": \"session=abc123; HttpOnly\" },\n" +
        "            { \"key\": \"Content-Type\", \"value\": \"application/json\" }\n" +
        "          ],\n" +
        "          \"body\": \"{\\\"access_token\\\":\\\"at-secret-999\\\",\\\"expires_in\\\":3600}\"\n" +
        "        }\n" +
        "      ]\n" +
        "    }\n" +
        "  ]\n" +
        "}";

    // --- HAR ---

    @Test
    public void harRedactsSensitiveRequestHeaderByDefault() {
        // Authorization is dropped by HAR's volatile-request-header filter, so this
        // exercises a sensitive header that survives filtering (X-Api-Key) and must
        // then be masked by redaction rather than leaked verbatim.
        HttpRequest request = (HttpRequest) harImporter.importExpectations(SENSITIVE_HAR).get(0).getHttpRequest();

        assertThat(request.getFirstHeader("X-Api-Key"), is(PLACEHOLDER));
    }

    @Test
    public void harRedactsSensitiveRequestBodyFieldByDefault() {
        HttpRequest request = (HttpRequest) harImporter.importExpectations(SENSITIVE_HAR).get(0).getHttpRequest();

        assertThat(request.getBodyAsString(), allOf(
            not(containsString("sk-live-12345")),
            containsString(PLACEHOLDER),
            // non-sensitive body fields are preserved
            containsString("alice")
        ));
    }

    @Test
    public void harRedactsSensitiveResponseHeaderByDefault() {
        // Set-Cookie is dropped by HAR's volatile-response-header filter, so this
        // exercises a sensitive header that survives filtering (X-Api-Key) and must
        // then be masked by redaction.
        HttpResponse response = harImporter.importExpectations(SENSITIVE_HAR).get(0).getHttpResponse();

        assertThat(response.getFirstHeader("X-Api-Key"), is(PLACEHOLDER));
    }

    @Test
    public void harRedactsSensitiveResponseBodyFieldByDefault() {
        HttpResponse response = harImporter.importExpectations(SENSITIVE_HAR).get(0).getHttpResponse();

        assertThat(response.getBodyAsString(), allOf(
            not(containsString("at-secret-999")),
            containsString(PLACEHOLDER),
            containsString("expires_in")
        ));
    }

    @Test
    public void harPreservesNonSensitiveHeader() {
        HttpRequest request = (HttpRequest) harImporter.importExpectations(SENSITIVE_HAR).get(0).getHttpRequest();

        assertThat(request.getFirstHeader("X-Tenant"), is("acme"));
    }

    @Test
    public void harPreservesExpectationIdAfterRedaction() {
        Expectation expectation = harImporter.importExpectations(SENSITIVE_HAR).get(0);

        assertThat(expectation.getId(), matchesPattern("har-[0-9a-f]{12}"));
    }

    @Test
    public void harOptOutKeepsOriginalSecrets() {
        Expectation expectation = harImporter
            .importExpectations(SENSITIVE_HAR, ImportRedaction.Options.disabled())
            .get(0);

        HttpRequest request = (HttpRequest) expectation.getHttpRequest();
        // X-Api-Key survives HAR's header filter; with redaction off it stays verbatim.
        assertThat(request.getFirstHeader("X-Api-Key"), is("req-secret-key"));
        assertThat(request.getBodyAsString(), containsString("sk-live-12345"));

        HttpResponse response = expectation.getHttpResponse();
        assertThat(response.getFirstHeader("X-Api-Key"), is("resp-secret-key"));
        assertThat(response.getBodyAsString(), containsString("at-secret-999"));
    }

    @Test
    public void harHonoursAdditionalSensitiveHeaderAndBodyField() {
        ImportRedaction.Options options = ImportRedaction.Options.enabled()
            .withAdditionalSensitiveHeaders(List.of("X-Tenant"))
            .withAdditionalSensitiveBodyFields(List.of("user"));

        HttpRequest request = (HttpRequest) harImporter.importExpectations(SENSITIVE_HAR, options).get(0).getHttpRequest();

        assertThat(request.getFirstHeader("X-Tenant"), is(PLACEHOLDER));
        assertThat(request.getBodyAsString(), not(containsString("alice")));
    }

    // --- Postman ---

    @Test
    public void postmanRedactsSensitiveRequestHeaderByDefault() {
        HttpRequest request = (HttpRequest) postmanImporter.importExpectations(SENSITIVE_POSTMAN).get(0).getHttpRequest();

        assertThat(request.getFirstHeader("Authorization"), is(PLACEHOLDER));
    }

    @Test
    public void postmanRedactsSensitiveRequestBodyFieldByDefault() {
        HttpRequest request = (HttpRequest) postmanImporter.importExpectations(SENSITIVE_POSTMAN).get(0).getHttpRequest();

        assertThat(request.getBodyAsString(), allOf(
            not(containsString("sk-live-12345")),
            containsString(PLACEHOLDER),
            containsString("alice")
        ));
    }

    @Test
    public void postmanRedactsSensitiveResponseHeaderByDefault() {
        HttpResponse response = postmanImporter.importExpectations(SENSITIVE_POSTMAN).get(0).getHttpResponse();

        assertThat(response.getFirstHeader("Set-Cookie"), is(PLACEHOLDER));
    }

    @Test
    public void postmanRedactsSensitiveResponseBodyFieldByDefault() {
        HttpResponse response = postmanImporter.importExpectations(SENSITIVE_POSTMAN).get(0).getHttpResponse();

        assertThat(response.getBodyAsString(), allOf(
            not(containsString("at-secret-999")),
            containsString(PLACEHOLDER)
        ));
    }

    @Test
    public void postmanPreservesNonSensitiveHeader() {
        HttpRequest request = (HttpRequest) postmanImporter.importExpectations(SENSITIVE_POSTMAN).get(0).getHttpRequest();

        assertThat(request.getFirstHeader("X-Tenant"), is("acme"));
    }

    @Test
    public void postmanPreservesExpectationIdAfterRedaction() {
        Expectation expectation = postmanImporter.importExpectations(SENSITIVE_POSTMAN).get(0);

        assertThat(expectation.getId(), matchesPattern("postman-login-[0-9a-f]{12}"));
    }

    @Test
    public void postmanOptOutKeepsOriginalSecrets() {
        Expectation expectation = postmanImporter
            .importExpectations(SENSITIVE_POSTMAN, ImportRedaction.Options.disabled())
            .get(0);

        HttpRequest request = (HttpRequest) expectation.getHttpRequest();
        assertThat(request.getFirstHeader("Authorization"), is("Bearer super-secret-token"));
        assertThat(request.getBodyAsString(), containsString("sk-live-12345"));

        HttpResponse response = expectation.getHttpResponse();
        assertThat(response.getFirstHeader("Set-Cookie"), is("session=abc123; HttpOnly"));
        assertThat(response.getBodyAsString(), containsString("at-secret-999"));
    }

    @Test
    public void preservingRedactionMasksSecretsInsideXmlAndBinaryMatchersButKeepsTheirType() {
        List<Expectation> redacted = ImportRedaction.redactPreservingActions(List.of(
            new Expectation(HttpRequest.request("/xml").withBody(XmlBody.xml("<login><password>HUNTER2</password></login>"))).thenRespond(HttpResponse.response()),
            new Expectation(HttpRequest.request("/bin").withBody(BinaryBody.binary("{\"api_key\":\"BINSECRET\"}".getBytes(UTF_8)))).thenRespond(HttpResponse.response())
        ), ImportRedaction.Options.enabled());

        Body<?> xml = ((HttpRequest) redacted.get(0).getHttpRequest()).getBody();
        assertThat(xml, instanceOf(XmlBody.class));
        assertThat(xml.toString(), not(containsString("HUNTER2")));

        Body<?> binary = ((HttpRequest) redacted.get(1).getHttpRequest()).getBody();
        assertThat(binary, instanceOf(BinaryBody.class));
        assertThat(new String(binary.getRawBytes(), UTF_8), is("{\"api_key\":\"" + PLACEHOLDER + "\"}"));
    }

    @Test
    public void preservingRedactionKeepsJsonMatcherTypeAndFlagsAndExpressionMatchers() {
        JsonBody json = JsonBody.json("{\"password\":\"pw\",\"user\":\"a\"}", MatchType.ONLY_MATCHING_FIELDS);
        json.withOptional(true);
        json.setNot(true);
        List<Expectation> redacted = ImportRedaction.redactPreservingActions(List.of(
            new Expectation(HttpRequest.request("/json").withBody(json)).thenRespond(HttpResponse.response()),
            new Expectation(HttpRequest.request("/xpath").withBody(XPathBody.xpath("//password[text()='x']"))).thenRespond(HttpResponse.response())
        ), ImportRedaction.Options.enabled());

        JsonBody redactedJson = (JsonBody) ((HttpRequest) redacted.get(0).getHttpRequest()).getBody();
        assertThat(redactedJson.getValue(), not(containsString("\"pw\"")));
        assertThat(redactedJson.getMatchType(), is(MatchType.ONLY_MATCHING_FIELDS));
        assertThat(redactedJson.getOptional(), is(true));
        assertThat(redactedJson.isNot(), is(true));
        assertThat(((HttpRequest) redacted.get(1).getHttpRequest()).getBody(), is(XPathBody.xpath("//password[text()='x']")));
    }
}
