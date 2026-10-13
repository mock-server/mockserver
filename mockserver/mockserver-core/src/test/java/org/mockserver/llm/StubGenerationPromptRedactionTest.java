package org.mockserver.llm;

import org.junit.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.BinaryBody.binary;

/**
 * Redaction of credentials from the {@code /generateExpectation} prompt before it is
 * sent to an external LLM backend. See {@link LlmPromptRedactor}.
 */
public class StubGenerationPromptRedactionTest {

    private static final String REDACTED = "***REDACTED***";

    private final StubGenerationPromptBuilder builder = new StubGenerationPromptBuilder();

    @Test
    public void shouldRedactEachSensitiveHeaderKeepingItsName() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api")
            .withHeader("Authorization", "Bearer sk-secret-1")
            .withHeader("Proxy-Authorization", "Basic proxy-secret")
            .withHeader("Cookie", "session=cookie-secret")
            .withHeader("Set-Cookie", "session=setcookie-secret")
            .withHeader("x-api-key", "xapikey-secret")
            .withHeader("api-key", "apikey-secret");

        String prompt = builder.build(request, Collections.emptyList());

        for (String name : new String[]{"Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie", "x-api-key", "api-key"}) {
            assertThat(prompt, containsString(name));
        }
        assertThat(prompt, not(containsString("sk-secret-1")));
        assertThat(prompt, not(containsString("proxy-secret")));
        assertThat(prompt, not(containsString("cookie-secret")));
        assertThat(prompt, not(containsString("setcookie-secret")));
        assertThat(prompt, not(containsString("xapikey-secret")));
        assertThat(prompt, not(containsString("apikey-secret")));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldLeaveNonSensitiveHeadersUnchanged() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api")
            .withHeader("Accept", "application/json")
            .withHeader("User-Agent", "curl/8.0")
            .withHeader("Authorization", "Bearer sk-secret-2");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("application/json"));
        assertThat(prompt, containsString("curl/8.0"));
        assertThat(prompt, not(containsString("sk-secret-2")));
    }

    @Test
    public void shouldRedactSensitiveHeaderCaseInsensitively() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api")
            .withHeader("AUTHORIZATION", "Bearer sk-mixed-case")
            .withHeader("X-Api-Key", "key-mixed-case");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("sk-mixed-case")));
        assertThat(prompt, not(containsString("key-mixed-case")));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldRedactConfiguredDataPlaneApiKeyHeader() {
        StubGenerationPromptBuilder configured = new StubGenerationPromptBuilder("X-Company-Auth");
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api")
            .withHeader("X-Company-Auth", "company-secret-token");

        String prompt = configured.build(request, Collections.emptyList());

        assertThat(prompt, containsString("X-Company-Auth"));
        assertThat(prompt, not(containsString("company-secret-token")));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldMaskUrlUserInfoInRequestPath() {
        HttpRequest request = HttpRequest.request().withMethod("GET")
            .withPath("http://alice:hunter2@internal.example.com/v1/resource");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("hunter2")));
        assertThat(prompt, not(containsString("alice:hunter2")));
        assertThat(prompt, containsString("internal.example.com"));
        assertThat(prompt, containsString(REDACTED + "@internal.example.com"));
    }

    @Test
    public void shouldRedactContextExpectationUrlCredentials() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api/items");
        List<Expectation> context = Collections.singletonList(new Expectation(
            HttpRequest.request().withMethod("GET")
                .withPath("https://svc:ctx-secret@upstream.example.com/orders")));

        String prompt = builder.build(request, context);

        assertThat(prompt, containsString("EXISTING EXPECTATIONS"));
        assertThat(prompt, not(containsString("ctx-secret")));
        assertThat(prompt, containsString("upstream.example.com"));
    }

    @Test
    public void shouldMaskNestedJsonCredentialFields() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/api/login")
            .withBody("{\"user\":\"bob\",\"password\":\"top-secret-pw\",\"nested\":{\"api_key\":\"deep-key\",\"note\":\"ok\"}}");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("bob"));
        assertThat(prompt, containsString("ok"));
        assertThat(prompt, not(containsString("top-secret-pw")));
        assertThat(prompt, not(containsString("deep-key")));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldMaskJwtAnywhereInBody() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/api/data")
            .withBody("{\"note\":\"bearer " + jwt + " here\"}");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString(jwt)));
        assertThat(prompt, not(containsString("eyJhbGci")));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldOmitUnparseableJsonBody() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/api/data")
            .withBody("{ this is not valid json password: leaked-secret ");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("[body omitted: could not be parsed for redaction]"));
        assertThat(prompt, not(containsString("leaked-secret")));
    }

    @Test
    public void shouldMaskCredentialsInFormBody() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/oauth/token")
            .withBody("grant_type=password&username=bob&password=form-secret&client_secret=cs-secret");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("username=bob"));
        assertThat(prompt, not(containsString("form-secret")));
        assertThat(prompt, not(containsString("cs-secret")));
        assertThat(prompt, containsString("password=" + REDACTED));
    }

    @Test
    public void shouldNotMutateServedRequestOrItsBody() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/api/login")
            .withHeader("Authorization", "Bearer sk-original")
            .withBody("{\"password\":\"original-pw\"}");

        builder.build(request, Collections.emptyList());

        assertThat(request.getFirstHeader("Authorization"), is("Bearer sk-original"));
        assertThat(request.getBodyAsString(), is("{\"password\":\"original-pw\"}"));
    }

    @Test
    public void shouldRedactAdditionalSensitiveHeaders() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api")
            .withHeader("X-Auth-Token", "xauth-secret")
            .withHeader("X-Access-Token", "xaccess-secret")
            .withHeader("X-Amz-Security-Token", "xamz-secret")
            .withHeader("X-Csrf-Token", "xcsrf-secret");

        String prompt = builder.build(request, Collections.emptyList());

        for (String name : new String[]{"X-Auth-Token", "X-Access-Token", "X-Amz-Security-Token", "X-Csrf-Token"}) {
            assertThat(prompt, containsString(name));
        }
        assertThat(prompt, not(containsString("xauth-secret")));
        assertThat(prompt, not(containsString("xaccess-secret")));
        assertThat(prompt, not(containsString("xamz-secret")));
        assertThat(prompt, not(containsString("xcsrf-secret")));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldMaskSensitiveQueryParameters() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api/search")
            .withQueryStringParameter("api_key", "qp-apikey-secret")
            .withQueryStringParameter("token", "qp-token-secret")
            .withQueryStringParameter("access_token", "qp-access-secret")
            .withQueryStringParameter("q", "hello");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("Query Parameters"));
        assertThat(prompt, containsString("hello"));
        assertThat(prompt, not(containsString("qp-apikey-secret")));
        assertThat(prompt, not(containsString("qp-token-secret")));
        assertThat(prompt, not(containsString("qp-access-secret")));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldOmitBinaryBody() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/api/upload")
            .withBody(binary(new byte[]{0, 1, 2, 3, 4, 5}));

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("Request Body:")));
    }

    @Test
    public void shouldRedactMultiValueSensitiveHeader() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api")
            .withHeader("Authorization", "Bearer first-secret", "Bearer second-secret");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("Authorization"));
        assertThat(prompt, not(containsString("first-secret")));
        assertThat(prompt, not(containsString("second-secret")));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldMaskCredentialsInStringifiedJsonValue() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/api/data")
            .withBody("{\"data\":\"{\\\"password\\\":\\\"nested-secret\\\",\\\"keep\\\":\\\"ok\\\"}\"}");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("nested-secret")));
        assertThat(prompt, containsString("ok"));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldMaskUnderscorePrefixedKeyButNotTokenizer() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/oauth/token")
            .withBody("my_password=underscore-secret&tokenizer=on&x-api_key=dash-secret");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("underscore-secret")));
        assertThat(prompt, not(containsString("dash-secret")));
        assertThat(prompt, containsString("tokenizer=on"));
    }

    @Test
    public void shouldMaskJwtAndKeyValueCredentialsInRequestAndContextPaths() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwYXRoIn0.pathJwtSignature_-123";
        HttpRequest request = HttpRequest.request().withMethod("GET")
            .withPath("/magic-link/" + jwt + "/session;token=path-kv-secret");
        List<Expectation> context = Collections.singletonList(new Expectation(
            HttpRequest.request().withMethod("GET").withPath("/reset;access_token=ctx-kv-secret")));

        String prompt = builder.build(request, context);

        assertThat(prompt, not(containsString(jwt)));
        assertThat(prompt, not(containsString("path-kv-secret")));
        assertThat(prompt, not(containsString("ctx-kv-secret")));
        assertThat(prompt, containsString("Path: /magic-link/" + REDACTED + "/session;token=" + REDACTED));
        assertThat(prompt, containsString("- GET /reset;access_token=" + REDACTED));
    }

    @Test
    public void shouldMaskQueryParametersNamedLikeBodyCredentialFields() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api/callback")
            .withQueryStringParameter("password", "qp-password-secret")
            .withQueryStringParameter("client_secret", "qp-client-secret")
            .withQueryStringParameter("refresh_token", "qp-refresh-secret")
            .withQueryStringParameter("id_token", "qp-idtoken-secret")
            .withQueryStringParameter("page", "2");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("qp-password-secret")));
        assertThat(prompt, not(containsString("qp-client-secret")));
        assertThat(prompt, not(containsString("qp-refresh-secret")));
        assertThat(prompt, not(containsString("qp-idtoken-secret")));
        assertThat(prompt, containsString("page"));
        assertThat(prompt, containsString("\"2\""));
    }

    @Test
    public void shouldMaskJwtInAnyQueryParameter() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdGF0ZSI6InEifQ.queryJwtSignature_-456";
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api/search")
            .withQueryStringParameter("filter", jwt)
            .withQueryStringParameter("page", "plain-page-value");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString(jwt)));
        assertThat(prompt, containsString("filter"));
        assertThat(prompt, containsString("plain-page-value"));
        assertThat(prompt, containsString(REDACTED));
    }

    @Test
    public void shouldMaskJwtAndKeyValueCredentialsInNonSensitiveHeaderValues() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzZXNzIjoiaCJ9.headerJwtSignature_-789";
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api")
            .withHeader("X-Session", jwt)
            .withHeader("X-Debug", "user=bob token=hdr-kv-secret")
            .withHeader("Accept", "text/html;q=0.9");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString(jwt)));
        assertThat(prompt, not(containsString("hdr-kv-secret")));
        assertThat(prompt, containsString("user=bob token=" + REDACTED));
        assertThat(prompt, containsString("text/html;q=0.9"));
    }

    @Test
    public void shouldLeaveOrdinaryPathsUnchanged() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api/v1.2/orders/42");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("Path: /api/v1.2/orders/42\n"));
    }

    @Test
    public void shouldMaskAuthorizationCodeQueryParameterBesideEachOAuthSignal() {
        List<HttpRequest> oauthRequests = List.of(
            HttpRequest.request().withPath("/oauth/callback").withQueryStringParameter("code", "auth-code-secret"),
            HttpRequest.request().withPath("/login/oauth2/code/google").withQueryStringParameter("code", "auth-code-secret"),
            HttpRequest.request().withPath("/authorize").withQueryStringParameter("code", "auth-code-secret"),
            HttpRequest.request().withPath("/auth/Callback").withQueryStringParameter("code", "auth-code-secret"),
            HttpRequest.request().withPath("/return").withQueryStringParameter("code", "auth-code-secret")
                .withQueryStringParameter("state", "xyz"),
            HttpRequest.request().withPath("/return").withQueryStringParameter("code", "auth-code-secret")
                .withQueryStringParameter("redirect_uri", "https://app.example/return"),
            HttpRequest.request().withPath("/return").withQueryStringParameter("code", "auth-code-secret")
                .withQueryStringParameter("client_id", "web-app"),
            HttpRequest.request().withPath("/token").withQueryStringParameter("code", "auth-code-secret")
                .withQueryStringParameter("grant_type", "authorization_code")
        );

        for (HttpRequest request : oauthRequests) {
            String prompt = builder.build(request.withMethod("GET"), Collections.emptyList());

            assertThat(request.toString(), prompt, not(containsString("auth-code-secret")));
            assertThat(request.toString(), prompt, containsString("code"));
            assertThat(request.toString(), prompt, containsString(REDACTED));
        }
    }

    @Test
    public void shouldKeepCodeQueryParameterWithoutOAuthSignalVisible() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api/countries")
            .withQueryStringParameter("code", "GB")
            .withQueryStringParameter("grant_type", "client_credentials")
            .withQueryStringParameter("statement", "monthly");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("\"GB\""));
        assertThat(prompt, not(containsString(REDACTED)));
    }

    @Test
    public void shouldMaskAuthorizationCodeInFormBodyBesideOAuthSignal() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/token")
            .withBody("grant_type=authorization_code&code=form-code-secret&redirect_uri=https%3A%2F%2Fapp.example%2Fcb");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("form-code-secret")));
        assertThat(prompt, containsString("grant_type=authorization_code&code=" + REDACTED + "&redirect_uri="));
    }

    @Test
    public void shouldMaskAuthorizationCodeInFormBodyOfOAuthPath() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/oauth2/token")
            .withBody("code=path-context-code-secret");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("path-context-code-secret")));
        assertThat(prompt, containsString("Request Body: code=" + REDACTED));
    }

    @Test
    public void shouldKeepCodeInFormBodyWithoutOAuthSignalVisible() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/api/vouchers")
            .withBody("code=SUMMER10&error_code=E42&my_state=on");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("Request Body: code=SUMMER10&error_code=E42&my_state=on"));
    }

    @Test
    public void shouldMaskAuthorizationCodeInJsonBodyBesideOAuthSignal() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/token")
            .withBody("{\"grant_type\":\"authorization_code\",\"code\":\"json-code-secret\",\"meta\":{\"code\":\"GB\"}}");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("json-code-secret")));
        assertThat(prompt, containsString("\"code\":\"" + REDACTED + "\""));
        assertThat(prompt, containsString("\"meta\":{\"code\":\"GB\"}"));
    }

    @Test
    public void shouldKeepCodeInJsonBodyWithoutOAuthSignalVisible() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/api/countries")
            .withBody("{\"code\":\"GB\",\"grant_type\":\"client_credentials\"}");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("\"code\":\"GB\""));
    }

    @Test
    public void shouldMaskAuthorizationCodeInRedirectUrlHeaderValue() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/home")
            .withHeader("Referer", "https://app.example/callback?code=referer-code-secret&state=s1")
            .withHeader("X-Return", "https://app.example/landing?state=s2&code=return-code-secret#top")
            .withHeader("X-Filter", "code=GB&page=2")
            .withHeader("X-Error", "error_code=E42&state=s3");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("referer-code-secret")));
        assertThat(prompt, not(containsString("return-code-secret")));
        assertThat(prompt, containsString("https://app.example/callback?code=" + REDACTED + "&state=s1"));
        assertThat(prompt, containsString("code=GB&page=2"));
        assertThat(prompt, containsString("error_code=E42&state=s3"));
    }

    @Test
    public void shouldMaskAuthorizationCodeInRequestAndContextPaths() {
        HttpRequest request = HttpRequest.request().withMethod("GET")
            .withPath("/oauth/callback;code=path-code-secret");
        List<Expectation> context = List.of(
            new Expectation(HttpRequest.request().withMethod("GET").withPath("/sso/authorize;code=ctx-code-secret")),
            new Expectation(HttpRequest.request().withMethod("GET").withPath("/api/countries;code=GB")));

        String prompt = builder.build(request, context);

        assertThat(prompt, not(containsString("path-code-secret")));
        assertThat(prompt, not(containsString("ctx-code-secret")));
        assertThat(prompt, containsString("Path: /oauth/callback;code=" + REDACTED + "\n"));
        assertThat(prompt, containsString("- GET /api/countries;code=GB\n"));
    }

    @Test
    public void shouldMaskAuthorizationCodeBesideIssuerParameter() {
        HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/login")
            .withQueryStringParameter("code", "iss-code-secret")
            .withQueryStringParameter("iss", "https://idp.example")
            .withHeader("Referer", "https://app.example/login?code=iss-referer-secret&iss=https%3A%2F%2Fidp.example");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, not(containsString("iss-code-secret")));
        assertThat(prompt, not(containsString("iss-referer-secret")));
        assertThat(prompt, containsString("https://idp.example"));
    }

    @Test
    public void shouldKeepCodeBesideIssuerLikeParameterVisible() {
        HttpRequest request = HttpRequest.request().withMethod("POST").withPath("/api/tickets")
            .withBody("code=GB&issue=42&miss=1");

        String prompt = builder.build(request, Collections.emptyList());

        assertThat(prompt, containsString("Request Body: code=GB&issue=42&miss=1"));
    }

    @Test
    public void shouldMaskPkceCodeVerifierAsCredential() {
        HttpRequest formRequest = HttpRequest.request().withMethod("POST").withPath("/token")
            .withBody("grant_type=authorization_code&code=pkce-code-secret&code_verifier=form-verifier-secret");
        HttpRequest queryRequest = HttpRequest.request().withMethod("GET").withPath("/api/exchange")
            .withQueryStringParameter("code_verifier", "query-verifier-secret");
        HttpRequest jsonRequest = HttpRequest.request().withMethod("POST").withPath("/api/exchange")
            .withBody("{\"code_verifier\":\"json-verifier-secret\"}");

        String formPrompt = builder.build(formRequest, Collections.emptyList());
        String queryPrompt = builder.build(queryRequest, Collections.emptyList());
        String jsonPrompt = builder.build(jsonRequest, Collections.emptyList());

        assertThat(formPrompt, not(containsString("form-verifier-secret")));
        assertThat(formPrompt, not(containsString("pkce-code-secret")));
        assertThat(formPrompt, containsString("code_verifier=" + REDACTED));
        assertThat(queryPrompt, not(containsString("query-verifier-secret")));
        assertThat(queryPrompt, containsString("code_verifier"));
        assertThat(jsonPrompt, not(containsString("json-verifier-secret")));
        assertThat(jsonPrompt, containsString("\"code_verifier\":\"" + REDACTED + "\""));
    }
}
