package org.mockserver.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.HttpRequest.request;

public class OidcProviderGeneratorTest {

    private final OidcProviderGenerator generator = new OidcProviderGenerator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void resetStore() {
        OidcAuthorizationStore.getInstance().reset();
    }

    /**
     * Returns the token response a client receives from the /token endpoint for a non
     * authorization-code grant (e.g. client_credentials) — exercising the real callback.
     */
    /**
     * Renders the discovery document. Discovery is now served by {@link OidcDiscoveryCallback} rather
     * than a static response, so the issuer can be derived per request from the Host header (OIDC
     * Discovery 4.3 requires it to match the URL the relying party used). These tests previously read
     * a pre-baked body off the expectation.
     */
    private JsonNode discoveryDocument() throws Exception {
        return objectMapper.readTree(new OidcDiscoveryCallback()
            .handle(request()
                .withMethod("GET")
                .withPath("/.well-known/openid-configuration")
                .withHeader("host", "localhost:1080"))
            .getBodyAsString());
    }

    private String tokenResponseBody() throws Exception {
        return new OidcTokenCallback()
            .handle(request().withMethod("POST").withPath("/token").withBody("grant_type=client_credentials"))
            .getBodyAsString();
    }

    @Test
    public void defaultsProduceNineExpectations() {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration());

        assertThat(expectations.size(), is(9));
        assertThat(expectations.get(0).getId(), is("oidc.discovery"));
        assertThat(expectations.get(1).getId(), is("oidc.jwks"));
        assertThat(expectations.get(2).getId(), is("oidc.token"));
        assertThat(expectations.get(3).getId(), is("oidc.authorize"));
        assertThat(expectations.get(4).getId(), is("oidc.userinfo"));
        assertThat(expectations.get(5).getId(), is("oidc.introspect"));
        assertThat(expectations.get(6).getId(), is("oidc.revoke"));
        assertThat(expectations.get(7).getId(), is("oidc.logout"));
        assertThat(expectations.get(8).getId(), is("oidc.deviceAuthorization"));
    }

    @Test
    public void discoveryDocumentHasCorrectFields() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setIssuer("https://idp.example.com");

        List<Expectation> expectations = generator.generate(config);
        Expectation discovery = expectations.get(0);

        HttpRequest request = (HttpRequest) discovery.getHttpRequest();
        assertThat(request.getMethod().getValue(), is("GET"));
        assertThat(request.getPath().getValue(), is("/.well-known/openid-configuration"));

        // Discovery is a class callback so the issuer can be resolved per request; when an issuer is
        // configured explicitly (as here) it still wins, so the endpoint URLs are unchanged.
        assertThat(discovery.getHttpResponseClassCallback().getCallbackClass(),
            is(OidcDiscoveryCallback.class.getName()));

        JsonNode doc = discoveryDocument();
        assertThat(doc.get("issuer").asText(), is("https://idp.example.com"));
        assertThat(doc.get("jwks_uri").asText(), is("https://idp.example.com/.well-known/jwks.json"));
        assertThat(doc.get("token_endpoint").asText(), is("https://idp.example.com/token"));
        assertThat(doc.get("authorization_endpoint").asText(), is("https://idp.example.com/authorize"));
        assertThat(doc.get("userinfo_endpoint").asText(), is("https://idp.example.com/userinfo"));
        assertThat(doc.get("introspection_endpoint").asText(), is("https://idp.example.com/introspect"));
        assertThat(doc.get("revocation_endpoint").asText(), is("https://idp.example.com/revoke"));

        assertThat(doc.get("end_session_endpoint").asText(), is("https://idp.example.com/logout"));
        assertThat(doc.get("device_authorization_endpoint").asText(), is("https://idp.example.com/device_authorization"));

        // Check supported values — implemented grants advertised, including the RFC 8628 device-code grant.
        assertTrue(doc.get("grant_types_supported").isArray());
        assertThat(doc.get("grant_types_supported").size(), is(4));
        List<String> grants = new java.util.ArrayList<>();
        doc.get("grant_types_supported").forEach(node -> grants.add(node.asText()));
        assertThat(grants, hasItems("authorization_code", "client_credentials", "refresh_token",
            "urn:ietf:params:oauth:grant-type:device_code"));

        assertThat(doc.get("id_token_signing_alg_values_supported").get(0).asText(), is("RS256"));
        assertThat(doc.get("subject_types_supported").get(0).asText(), is("public"));

        // PKCE methods advertised (PKCE is implemented)
        List<String> pkceMethods = new java.util.ArrayList<>();
        doc.get("code_challenge_methods_supported").forEach(node -> pkceMethods.add(node.asText()));
        assertThat(pkceMethods, hasItems("S256", "plain"));

        // Token endpoint auth methods advertised
        List<String> authMethods = new java.util.ArrayList<>();
        doc.get("token_endpoint_auth_methods_supported").forEach(node -> authMethods.add(node.asText()));
        assertThat(authMethods, hasItems("client_secret_basic", "client_secret_post", "none"));

        assertTrue(doc.get("claims_supported").isArray());
    }

    @Test
    public void discoveryDocumentUsesCustomPaths() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setIssuer("https://auth.test")
            .setJwksPath("/custom/jwks")
            .setTokenPath("/custom/token")
            .setAuthorizePath("/custom/auth")
            .setUserinfoPath("/custom/me")
            .setIntrospectPath("/custom/introspect")
            .setRevokePath("/custom/revoke");

        List<Expectation> expectations = generator.generate(config);
        JsonNode doc = discoveryDocument();

        assertThat(doc.get("jwks_uri").asText(), is("https://auth.test/custom/jwks"));
        assertThat(doc.get("token_endpoint").asText(), is("https://auth.test/custom/token"));
        assertThat(doc.get("authorization_endpoint").asText(), is("https://auth.test/custom/auth"));
        assertThat(doc.get("userinfo_endpoint").asText(), is("https://auth.test/custom/me"));
        assertThat(doc.get("introspection_endpoint").asText(), is("https://auth.test/custom/introspect"));
        assertThat(doc.get("revocation_endpoint").asText(), is("https://auth.test/custom/revoke"));

        // Verify the endpoint paths themselves match
        assertThat(((HttpRequest) expectations.get(1).getHttpRequest()).getPath().getValue(), is("/custom/jwks"));
        assertThat(((HttpRequest) expectations.get(2).getHttpRequest()).getPath().getValue(), is("/custom/token"));
        assertThat(((HttpRequest) expectations.get(3).getHttpRequest()).getPath().getValue(), is("/custom/auth"));
        assertThat(((HttpRequest) expectations.get(4).getHttpRequest()).getPath().getValue(), is("/custom/me"));
        assertThat(((HttpRequest) expectations.get(5).getHttpRequest()).getPath().getValue(), is("/custom/introspect"));
        assertThat(((HttpRequest) expectations.get(6).getHttpRequest()).getPath().getValue(), is("/custom/revoke"));
    }

    @Test
    public void jwksContainsRsaPublicKey() throws Exception {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration());
        Expectation jwks = expectations.get(1);

        HttpRequest request = (HttpRequest) jwks.getHttpRequest();
        assertThat(request.getMethod().getValue(), is("GET"));
        assertThat(request.getPath().getValue(), is("/.well-known/jwks.json"));

        String jwksBody = jwks.getHttpResponse().getBodyAsString();
        JsonNode jwksJson = objectMapper.readTree(jwksBody);
        assertTrue("JWKS should have a 'keys' array", jwksJson.has("keys"));
        assertThat(jwksJson.get("keys").size(), is(1));

        JsonNode key = jwksJson.get("keys").get(0);
        assertThat(key.get("kty").asText(), is("RSA"));
        assertThat(key.get("use").asText(), is("sig"));
        assertTrue("Key should have 'n' (modulus)", key.has("n"));
        assertTrue("Key should have 'e' (exponent)", key.has("e"));
    }

    @Test
    public void accessTokenVerifiesAgainstJwks() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setIssuer("https://idp.test")
            .setSubject("test-user")
            .setAudience("test-audience");

        List<Expectation> expectations = generator.generate(config);

        // Parse JWKS
        String jwksBody = expectations.get(1).getHttpResponse().getBodyAsString();
        JWKSet jwkSet = JWKSet.parse(jwksBody);
        JWK jwk = jwkSet.getKeys().get(0);
        RSAKey rsaKey = (RSAKey) jwk;
        JWSVerifier verifier = new RSASSAVerifier(rsaKey);

        // Parse token response served by the /token callback
        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        String accessTokenStr = tokenResponse.get("access_token").asText();
        assertThat(accessTokenStr, is(notNullValue()));

        // Verify the access token signature
        SignedJWT signedJWT = SignedJWT.parse(accessTokenStr);
        assertTrue("Access token should verify against JWKS public key", signedJWT.verify(verifier));

        // Verify claims
        assertThat(signedJWT.getJWTClaimsSet().getIssuer(), is("https://idp.test"));
        assertThat(signedJWT.getJWTClaimsSet().getSubject(), is("test-user"));
        assertThat(signedJWT.getJWTClaimsSet().getAudience().get(0), is("test-audience"));
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("scope"), is("openid profile email"));

        // exp should be in the future
        long expEpoch = signedJWT.getJWTClaimsSet().getExpirationTime().getTime() / 1000;
        assertThat(expEpoch, greaterThan(Instant.now().getEpochSecond()));
    }

    @Test
    public void idTokenVerifiesAgainstJwks() throws Exception {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration());

        String jwksBody = expectations.get(1).getHttpResponse().getBodyAsString();
        JWKSet jwkSet = JWKSet.parse(jwksBody);
        RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);
        JWSVerifier verifier = new RSASSAVerifier(rsaKey);

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        String idTokenStr = tokenResponse.get("id_token").asText();

        SignedJWT signedJWT = SignedJWT.parse(idTokenStr);
        assertTrue("ID token should verify against JWKS public key", signedJWT.verify(verifier));
    }

    @Test
    public void tokenResponseHasCorrectStructure() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setTokenExpirySeconds(7200)
            .setScopes(Arrays.asList("openid", "custom"));

        List<Expectation> expectations = generator.generate(config);

        HttpRequest request = (HttpRequest) expectations.get(2).getHttpRequest();
        assertThat(request.getMethod().getValue(), is("POST"));
        assertThat(request.getPath().getValue(), is("/token"));

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        assertThat(tokenResponse.get("token_type").asText(), is("Bearer"));
        assertThat(tokenResponse.get("expires_in").asInt(), is(7200));
        assertThat(tokenResponse.get("scope").asText(), is("openid custom"));
        assertTrue(tokenResponse.has("access_token"));
        assertTrue(tokenResponse.has("id_token"));
    }

    /**
     * This previously read the claims straight off a <b>static</b> userinfo response — i.e. it asserted
     * that userinfo serves the subject and every configured claim <b>without any token being
     * presented</b>, documenting the fail-open as if it were the contract. Userinfo is an OAuth2
     * protected resource (OIDC Core §5.3), so the claims are now only served to a caller holding a
     * valid bearer token; the rejection paths are covered by {@link OidcUserinfoCallbackTest}.
     */
    @Test
    public void userinfoReturnsSubjectAndAdditionalClaimsForAValidToken() throws Exception {
        Map<String, Serializable> additional = new HashMap<>();
        additional.put("email", "user@example.com");
        additional.put("name", "Test User");

        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setSubject("sub-123")
            .setAdditionalClaims(additional);

        List<Expectation> expectations = generator.generate(config);
        Expectation userinfo = expectations.get(4);

        HttpRequest request = (HttpRequest) userinfo.getHttpRequest();
        assertThat(request.getMethod().getValue(), is("GET"));
        assertThat(request.getPath().getValue(), is("/userinfo"));
        assertThat(userinfo.getHttpResponseClassCallback().getCallbackClass(),
            is(OidcUserinfoCallback.class.getName()));

        String accessToken = objectMapper.readTree(tokenResponseBody()).get("access_token").asText();
        HttpResponse response = new OidcUserinfoCallback().handle(request()
            .withMethod("GET").withPath("/userinfo")
            .withHeader("authorization", "Bearer " + accessToken));
        assertThat(response.getStatusCode(), is(200));

        JsonNode body = objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("sub").asText(), is("sub-123"));
        assertThat(body.get("email").asText(), is("user@example.com"));
        assertThat(body.get("name").asText(), is("Test User"));
    }

    /**
     * The counterpart the old static-response test could not have: userinfo must refuse a caller with
     * no bearer token rather than handing over the subject and claims.
     */
    @Test
    public void userinfoRejectsRequestWithoutABearerToken() {
        generator.generate(new OidcProviderConfiguration().setSubject("sub-123"));

        HttpResponse response = new OidcUserinfoCallback().handle(request()
            .withMethod("GET").withPath("/userinfo"));

        assertThat(response.getStatusCode(), is(401));
        assertThat(response.getBodyAsString(), not(containsString("sub-123")));
    }

    /**
     * Previously named {@code introspectionReturnsActiveTrue}, this asserted that introspecting with
     * <b>no token at all</b> returned {@code active:true} and {@code sub:mock-user} — i.e. it pinned the
     * defect in place: the endpoint reported a token active without being shown a token. RFC 7662 §2.1
     * makes {@code token} REQUIRED, so the corrected behaviour is {@code 400 invalid_request}.
     */
    @Test
    public void introspectionRequiresATokenParameter() throws Exception {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration());
        Expectation introspect = expectations.get(5);

        HttpRequest request = (HttpRequest) introspect.getHttpRequest();
        assertThat(request.getMethod().getValue(), is("POST"));
        assertThat(request.getPath().getValue(), is("/introspect"));
        assertThat(introspect.getHttpResponseClassCallback().getCallbackClass(),
            is(OidcIntrospectionCallback.class.getName()));

        HttpResponse response = new OidcIntrospectionCallback().handle(
            request().withMethod("POST").withPath("/introspect").withBody(""));
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("invalid_request"));
    }

    /**
     * The positive counterpart the old {@code introspectionReturnsActiveTrue} never had: a token that
     * the provider actually minted introspects as active, and the claims come from that token.
     */
    @Test
    public void introspectionReportsAMintedTokenActive() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        String accessToken = objectMapper.readTree(tokenResponseBody()).get("access_token").asText();

        HttpResponse response = new OidcIntrospectionCallback().handle(
            request().withMethod("POST").withPath("/introspect").withBody("token=" + enc(accessToken)));
        JsonNode body = objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("active").asBoolean(), is(true));
        assertThat(body.get("sub").asText(), is("mock-user"));
    }

    /**
     * Previously asserted only that {@code /revoke} was a static expectation returning 200 — which it
     * was, and which is exactly why revocation did nothing: a revoked token kept introspecting as
     * active. It is now a class callback that records the revocation (RFC 7009). The 200 is retained
     * (RFC 7009 §2.2 returns 200 whether or not the token was recognised, so the endpoint is not a
     * token oracle); what is new is that the revocation takes effect.
     */
    @Test
    public void revocationIsAClassCallbackAndReturns200() throws Exception {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration());
        Expectation revoke = expectations.get(6);

        HttpRequest request = (HttpRequest) revoke.getHttpRequest();
        assertThat(request.getMethod().getValue(), is("POST"));
        assertThat(request.getPath().getValue(), is("/revoke"));
        assertThat(revoke.getHttpResponseClassCallback().getCallbackClass(),
            is(OidcRevocationCallback.class.getName()));

        String accessToken = objectMapper.readTree(tokenResponseBody()).get("access_token").asText();
        HttpResponse response = new OidcRevocationCallback().handle(
            request().withMethod("POST").withPath("/revoke").withBody("token=" + enc(accessToken)));
        assertThat(response.getStatusCode(), is(200));

        JsonNode afterRevocation = objectMapper.readTree(new OidcIntrospectionCallback().handle(
            request().withMethod("POST").withPath("/introspect").withBody("token=" + enc(accessToken)))
            .getBodyAsString());
        assertThat(afterRevocation.get("active").asBoolean(), is(false));
    }

    // --- Authorize endpoint / authorization-code flow ---

    @Test
    public void authorizeExpectationIsGetClassCallback() {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration());
        Expectation authorize = expectations.get(3);

        assertThat(authorize.getId(), is("oidc.authorize"));
        HttpRequest request = (HttpRequest) authorize.getHttpRequest();
        assertThat(request.getMethod().getValue(), is("GET"));
        assertThat(request.getPath().getValue(), is("/authorize"));
        assertThat(authorize.getHttpResponseClassCallback().getCallbackClass(),
            is(OidcAuthorizationCodeCallback.class.getName()));
    }

    @Test
    public void authorizeRedirectsWithCodeAndState() {
        generator.generate(new OidcProviderConfiguration());

        HttpResponse response = new OidcAuthorizationCodeCallback().handle(
            request()
                .withMethod("GET")
                .withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("client_id", "mock-client")
                .withQueryStringParameter("redirect_uri", "https://app.example.com/callback")
                .withQueryStringParameter("scope", "openid profile")
                .withQueryStringParameter("state", "xyz")
        );

        assertThat(response.getStatusCode(), is(302));
        String location = response.getFirstHeader("location");
        assertThat(location, containsString("https://app.example.com/callback?"));
        assertThat(location, containsString("code=mock-auth-code-"));
        assertThat(location, containsString("state=xyz"));
    }

    @Test
    public void authorizeWithoutRedirectUriReturns400() {
        generator.generate(new OidcProviderConfiguration());

        HttpResponse response = new OidcAuthorizationCodeCallback().handle(
            request()
                .withMethod("GET")
                .withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("state", "xyz")
        );

        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("invalid_request"));
    }

    @Test
    public void authorizationCodeExchangeReturnsValidTokens() throws Exception {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration());

        // 1. authorize -> 302 with code
        HttpResponse authorizeResponse = new OidcAuthorizationCodeCallback().handle(
            request()
                .withMethod("GET")
                .withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("client_id", "mock-client")
                .withQueryStringParameter("redirect_uri", "https://app/cb")
                .withQueryStringParameter("state", "abc")
        );
        String code = extractCode(authorizeResponse.getFirstHeader("location"));

        // 2. token exchange with the code
        HttpResponse tokenResponse = new OidcTokenCallback().handle(
            request()
                .withMethod("POST")
                .withPath("/token")
                .withBody("grant_type=authorization_code&code=" + code + "&redirect_uri=" + enc("https://app/cb"))
        );

        assertThat(tokenResponse.getStatusCode(), is(200));
        JsonNode body = objectMapper.readTree(tokenResponse.getBodyAsString());
        assertThat(body.get("token_type").asText(), is("Bearer"));
        assertTrue(body.has("access_token"));
        assertTrue(body.has("id_token"));

        // token must verify against the JWKS public key
        JWKSet jwkSet = JWKSet.parse(expectations.get(1).getHttpResponse().getBodyAsString());
        JWSVerifier verifier = new RSASSAVerifier((RSAKey) jwkSet.getKeys().get(0));
        assertTrue(SignedJWT.parse(body.get("access_token").asText()).verify(verifier));
    }

    @Test
    public void authorizationCodeIsSingleUse() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        String code = extractCode(new OidcAuthorizationCodeCallback().handle(
            request().withMethod("GET").withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("redirect_uri", "https://app/cb")
        ).getFirstHeader("location"));

        String body = "grant_type=authorization_code&code=" + code + "&redirect_uri=" + enc("https://app/cb");
        assertThat(new OidcTokenCallback().handle(tokenRequest(body)).getStatusCode(), is(200));
        // second exchange of the same code must fail
        assertThat(new OidcTokenCallback().handle(tokenRequest(body)).getStatusCode(), is(400));
    }

    @Test
    public void authorizationCodeRejectsRedirectUriMismatch() {
        generator.generate(new OidcProviderConfiguration());

        String code = extractCode(new OidcAuthorizationCodeCallback().handle(
            request().withMethod("GET").withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("redirect_uri", "https://app/cb")
        ).getFirstHeader("location"));

        HttpResponse tokenResponse = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=authorization_code&code=" + code + "&redirect_uri=" + enc("https://evil/cb")
        ));
        assertThat(tokenResponse.getStatusCode(), is(400));
        assertThat(tokenResponse.getBodyAsString(), containsString("invalid_grant"));
    }

    @Test
    public void pkceS256RoundTripSucceeds() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String codeChallenge = s256(codeVerifier);

        String code = extractCode(new OidcAuthorizationCodeCallback().handle(
            request().withMethod("GET").withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("redirect_uri", "https://app/cb")
                .withQueryStringParameter("code_challenge", codeChallenge)
                .withQueryStringParameter("code_challenge_method", "S256")
        ).getFirstHeader("location"));

        HttpResponse tokenResponse = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=authorization_code&code=" + code
                + "&redirect_uri=" + enc("https://app/cb")
                + "&code_verifier=" + codeVerifier
        ));
        assertThat(tokenResponse.getStatusCode(), is(200));
        assertTrue(objectMapper.readTree(tokenResponse.getBodyAsString()).has("access_token"));
    }

    @Test
    public void pkceWrongVerifierFails() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        String codeChallenge = s256("the-correct-verifier-value-1234567890");

        String code = extractCode(new OidcAuthorizationCodeCallback().handle(
            request().withMethod("GET").withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("redirect_uri", "https://app/cb")
                .withQueryStringParameter("code_challenge", codeChallenge)
                .withQueryStringParameter("code_challenge_method", "S256")
        ).getFirstHeader("location"));

        HttpResponse tokenResponse = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=authorization_code&code=" + code
                + "&redirect_uri=" + enc("https://app/cb")
                + "&code_verifier=a-completely-different-wrong-verifier-0987"
        ));
        assertThat(tokenResponse.getStatusCode(), is(400));
        assertThat(tokenResponse.getBodyAsString(), containsString("invalid_grant"));
    }

    @Test
    public void pkceMissingVerifierFailsWhenChallengePresent() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        String code = extractCode(new OidcAuthorizationCodeCallback().handle(
            request().withMethod("GET").withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("redirect_uri", "https://app/cb")
                .withQueryStringParameter("code_challenge", s256("some-verifier-value-aaaaaaaaaaaaaaaaaaaa"))
                .withQueryStringParameter("code_challenge_method", "S256")
        ).getFirstHeader("location"));

        HttpResponse tokenResponse = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=authorization_code&code=" + code + "&redirect_uri=" + enc("https://app/cb")
        ));
        assertThat(tokenResponse.getStatusCode(), is(400));
    }

    @Test
    public void clientCredentialsGrantStillReturnsTokens() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        HttpResponse tokenResponse = new OidcTokenCallback().handle(
            tokenRequest("grant_type=client_credentials")
        );
        assertThat(tokenResponse.getStatusCode(), is(200));
        JsonNode body = objectMapper.readTree(tokenResponse.getBodyAsString());
        assertThat(body.get("token_type").asText(), is("Bearer"));
        assertTrue(body.has("access_token"));
    }

    @Test
    public void unknownCodeIsRejected() {
        generator.generate(new OidcProviderConfiguration());

        HttpResponse tokenResponse = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=authorization_code&code=never-issued&redirect_uri=" + enc("https://app/cb")
        ));
        assertThat(tokenResponse.getStatusCode(), is(400));
        assertThat(tokenResponse.getBodyAsString(), containsString("invalid_grant"));
    }

    // --- Negative testing flags ---

    @Test
    public void issueExpiredTokenProducesExpInThePast() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setIssueExpiredToken(true);

        generator.generate(config);

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        String accessTokenStr = tokenResponse.get("access_token").asText();
        SignedJWT signedJWT = SignedJWT.parse(accessTokenStr);

        long expEpoch = signedJWT.getJWTClaimsSet().getExpirationTime().getTime() / 1000;
        assertThat("exp should be in the past", expEpoch, lessThan(Instant.now().getEpochSecond()));
    }

    /**
     * Previously introspected with an <b>empty body</b> and relied on {@code active} being copied from
     * the {@code issueExpiredToken} config flag — so it passed without an expired token ever being
     * presented, and would have passed identically had the endpoint been shown a perfectly valid token.
     * It now presents the actual expired token and asserts it is rejected on its own {@code exp}.
     */
    @Test
    public void issueExpiredTokenIntrospectsAsInactive() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setIssueExpiredToken(true);

        generator.generate(config);

        String expiredToken = objectMapper.readTree(tokenResponseBody()).get("access_token").asText();

        HttpResponse response = new OidcIntrospectionCallback().handle(
            request().withMethod("POST").withPath("/introspect").withBody("token=" + enc(expiredToken)));
        JsonNode introspection = objectMapper.readTree(response.getBodyAsString());
        assertThat(introspection.get("active").asBoolean(), is(false));
    }

    @Test
    public void wrongIssuerProducesMismatchedIss() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setIssuer("https://correct.issuer")
            .setWrongIssuer(true);

        List<Expectation> expectations = generator.generate(config);

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        String accessTokenStr = tokenResponse.get("access_token").asText();
        SignedJWT signedJWT = SignedJWT.parse(accessTokenStr);

        // The token's iss should NOT match the configured issuer
        String tokenIssuer = signedJWT.getJWTClaimsSet().getIssuer();
        assertThat(tokenIssuer, is("https://correct.issuer/wrong"));

        // But the discovery document should still report the correct issuer
        JsonNode discovery = discoveryDocument();
        assertThat(discovery.get("issuer").asText(), is("https://correct.issuer"));
    }

    @Test
    public void tamperedSignatureFailsVerification() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setTamperedSignature(true);

        List<Expectation> expectations = generator.generate(config);

        String jwksBody = expectations.get(1).getHttpResponse().getBodyAsString();
        JWKSet jwkSet = JWKSet.parse(jwksBody);
        RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);
        JWSVerifier verifier = new RSASSAVerifier(rsaKey);

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        String accessTokenStr = tokenResponse.get("access_token").asText();

        SignedJWT signedJWT = SignedJWT.parse(accessTokenStr);
        assertFalse("Tampered signature should FAIL verification", signedJWT.verify(verifier));
    }

    @Test
    public void additionalClaimsAppearInToken() throws Exception {
        Map<String, Serializable> additional = new HashMap<>();
        additional.put("custom_claim", "custom_value");
        additional.put("role", "admin");

        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setAdditionalClaims(additional);

        generator.generate(config);

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        String accessTokenStr = tokenResponse.get("access_token").asText();
        SignedJWT signedJWT = SignedJWT.parse(accessTokenStr);

        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("custom_claim"), is("custom_value"));
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("role"), is("admin"));
    }

    @Test
    public void nullConfigThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
    }

    @Test
    public void staticResponsesHaveJsonContentType() {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration());

        for (Expectation expectation : expectations) {
            HttpResponse response = expectation.getHttpResponse();
            if (response == null) {
                // /token and /authorize are class callbacks, not static responses
                continue;
            }
            String contentType = response.getFirstHeader("content-type");
            assertThat(
                "Expectation " + expectation.getId() + " should have JSON content type",
                contentType,
                containsString("application/json")
            );
        }
    }

    // --- token correctness, nonce, configurable signing, refresh tokens ---

    @Test
    public void idTokenAudIsClientIdAndAccessTokenAudIsAudience() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setClientId("my-client-id")
            .setAudience("my-resource-audience");
        generator.generate(config);

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        SignedJWT accessToken = SignedJWT.parse(tokenResponse.get("access_token").asText());
        SignedJWT idToken = SignedJWT.parse(tokenResponse.get("id_token").asText());

        assertThat(accessToken.getJWTClaimsSet().getAudience().get(0), is("my-resource-audience"));
        assertThat(accessToken.getJWTClaimsSet().getStringClaim("client_id"), is("my-client-id"));
        assertThat(idToken.getJWTClaimsSet().getAudience().get(0), is("my-client-id"));
    }

    @Test
    public void bothTokensHaveNbfEqualToIat() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        SignedJWT accessToken = SignedJWT.parse(tokenResponse.get("access_token").asText());
        SignedJWT idToken = SignedJWT.parse(tokenResponse.get("id_token").asText());

        assertThat(accessToken.getJWTClaimsSet().getNotBeforeTime(), is(notNullValue()));
        assertThat(accessToken.getJWTClaimsSet().getNotBeforeTime(),
            is(accessToken.getJWTClaimsSet().getIssueTime()));
        assertThat(idToken.getJWTClaimsSet().getNotBeforeTime(), is(notNullValue()));
        assertThat(idToken.getJWTClaimsSet().getNotBeforeTime(),
            is(idToken.getJWTClaimsSet().getIssueTime()));
    }

    @Test
    public void nonceFromAuthorizeAppearsInIdTokenAfterCodeExchange() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        String code = extractCode(new OidcAuthorizationCodeCallback().handle(
            request().withMethod("GET").withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("redirect_uri", "https://app/cb")
                .withQueryStringParameter("scope", "openid profile")
                .withQueryStringParameter("nonce", "n-0S6_WzA2Mj")
        ).getFirstHeader("location"));

        HttpResponse tokenResponse = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=authorization_code&code=" + code + "&redirect_uri=" + enc("https://app/cb")
        ));
        JsonNode body = objectMapper.readTree(tokenResponse.getBodyAsString());
        SignedJWT idToken = SignedJWT.parse(body.get("id_token").asText());
        assertThat(idToken.getJWTClaimsSet().getStringClaim("nonce"), is("n-0S6_WzA2Mj"));
    }

    @Test
    public void authorizationCodeGrantReturnsRefreshToken() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        String code = extractCode(new OidcAuthorizationCodeCallback().handle(
            request().withMethod("GET").withPath("/authorize")
                .withQueryStringParameter("response_type", "code")
                .withQueryStringParameter("redirect_uri", "https://app/cb")
                .withQueryStringParameter("scope", "openid")
        ).getFirstHeader("location"));

        JsonNode body = objectMapper.readTree(new OidcTokenCallback().handle(tokenRequest(
            "grant_type=authorization_code&code=" + code + "&redirect_uri=" + enc("https://app/cb")
        )).getBodyAsString());
        assertTrue("authorization_code grant should return a refresh_token", body.has("refresh_token"));
        assertThat(body.get("refresh_token").asText(), containsString("mock-refresh-"));
    }

    @Test
    public void refreshTokenGrantReturnsRefreshToken() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        JsonNode body = objectMapper.readTree(new OidcTokenCallback().handle(tokenRequest(
            "grant_type=refresh_token&refresh_token=mock-refresh-abc"
        )).getBodyAsString());
        assertTrue("refresh_token grant should return a refresh_token", body.has("refresh_token"));
        assertTrue(body.has("access_token"));
    }

    @Test
    public void clientCredentialsWithoutOpenidScopeOmitsIdToken() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        JsonNode body = objectMapper.readTree(new OidcTokenCallback().handle(tokenRequest(
            "grant_type=client_credentials&scope=" + enc("api.read api.write")
        )).getBodyAsString());
        assertTrue(body.has("access_token"));
        assertFalse("id_token should be omitted when openid scope absent", body.has("id_token"));
        assertFalse("client_credentials should not return a refresh_token", body.has("refresh_token"));
    }

    @Test
    public void configurableSigningAlgorithmEs256VerifiesAgainstEcJwks() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setSigningAlgorithm("ES256");
        List<Expectation> expectations = generator.generate(config);

        // JWKS should be an EC key
        JWKSet jwkSet = JWKSet.parse(expectations.get(1).getHttpResponse().getBodyAsString());
        JWK jwk = jwkSet.getKeys().get(0);
        assertThat(jwk.getKeyType().getValue(), is("EC"));
        JWSVerifier verifier = new ECDSAVerifier((ECKey) jwk);

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        SignedJWT accessToken = SignedJWT.parse(tokenResponse.get("access_token").asText());
        assertThat(accessToken.getHeader().getAlgorithm().getName(), is("ES256"));
        assertTrue("ES256 access token should verify against the EC JWKS", accessToken.verify(verifier));

        SignedJWT idToken = SignedJWT.parse(tokenResponse.get("id_token").asText());
        assertTrue("ES256 id token should verify against the EC JWKS", idToken.verify(verifier));

        // discovery should reflect the configured algorithm
        JsonNode discovery = discoveryDocument();
        assertThat(discovery.get("id_token_signing_alg_values_supported").get(0).asText(), is("ES256"));
    }

    @Test
    public void atHashUsesSha384DigestForEs384Provider() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setSigningAlgorithm("ES384");
        generator.generate(config);

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        String accessTokenStr = tokenResponse.get("access_token").asText();
        SignedJWT idToken = SignedJWT.parse(tokenResponse.get("id_token").asText());

        String atHash = idToken.getJWTClaimsSet().getStringClaim("at_hash");
        assertThat("id_token must carry an at_hash", atHash, is(notNullValue()));

        // Independently compute the OIDC at_hash for ES384: base64url(no-pad) of the left-most half
        // of SHA-384 of the ASCII access token.
        MessageDigest sha384 = MessageDigest.getInstance("SHA-384");
        byte[] hash = sha384.digest(accessTokenStr.getBytes(StandardCharsets.US_ASCII));
        byte[] leftHalf = Arrays.copyOf(hash, hash.length / 2);
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf);

        assertThat("ES384 at_hash must use SHA-384, not SHA-256", atHash, is(expected));

        // Sanity: the (incorrect) SHA-256-based hash must NOT match, proving the fix is exercised.
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash256 = sha256.digest(accessTokenStr.getBytes(StandardCharsets.US_ASCII));
        String sha256AtHash = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(Arrays.copyOf(hash256, hash256.length / 2));
        assertThat(atHash, is(not(sha256AtHash)));
    }

    @Test
    public void atHashUsesSha256DigestForDefaultRs256Provider() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        String accessTokenStr = tokenResponse.get("access_token").asText();
        SignedJWT idToken = SignedJWT.parse(tokenResponse.get("id_token").asText());

        String atHash = idToken.getJWTClaimsSet().getStringClaim("at_hash");
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(accessTokenStr.getBytes(StandardCharsets.US_ASCII));
        String expected = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(Arrays.copyOf(hash, hash.length / 2));
        assertThat("RS256 at_hash must use SHA-256 (default path unchanged)", atHash, is(expected));
    }

    @Test
    public void suppliedFixedKeyIdIsStableAcrossGenerateCalls() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setKeyId("stable-kid-123");

        List<Expectation> first = generator.generate(config);
        List<Expectation> second = generator.generate(config);

        String firstKid = objectMapper.readTree(first.get(1).getHttpResponse().getBodyAsString())
            .get("keys").get(0).get("kid").asText();
        String secondKid = objectMapper.readTree(second.get(1).getHttpResponse().getBodyAsString())
            .get("keys").get(0).get("kid").asText();

        assertThat(firstKid, is("stable-kid-123"));
        assertThat(secondKid, is("stable-kid-123"));
    }

    @Test
    public void logoutWithoutRedirectReturns200() {
        generator.generate(new OidcProviderConfiguration());

        HttpResponse response = new OidcLogoutCallback().handle(
            request().withMethod("GET").withPath("/logout"));
        assertThat(response.getStatusCode(), is(200));
    }

    @Test
    public void logoutWithPostLogoutRedirectUriRedirects() {
        generator.generate(new OidcProviderConfiguration());

        HttpResponse response = new OidcLogoutCallback().handle(
            request().withMethod("GET").withPath("/logout")
                .withQueryStringParameter("post_logout_redirect_uri", "https://app/home")
                .withQueryStringParameter("state", "s1"));
        assertThat(response.getStatusCode(), is(302));
        assertThat(response.getFirstHeader("location"), containsString("https://app/home"));
        assertThat(response.getFirstHeader("location"), containsString("state=s1"));
    }

    // --- device grant, client authentication, opaque access tokens ---

    @Test
    public void deviceAuthorizationExpectationIsPostClassCallback() {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration());
        Expectation device = expectations.get(8);

        assertThat(device.getId(), is("oidc.deviceAuthorization"));
        HttpRequest request = (HttpRequest) device.getHttpRequest();
        assertThat(request.getMethod().getValue(), is("POST"));
        assertThat(request.getPath().getValue(), is("/device_authorization"));
        assertThat(device.getHttpResponseClassCallback().getCallbackClass(),
            is(OidcDeviceAuthorizationCallback.class.getName()));
    }

    @Test
    public void deviceAuthorizationReturnsRfc8628Fields() throws Exception {
        generator.generate(new OidcProviderConfiguration().setIssuer("https://idp.test"));

        HttpResponse response = new OidcDeviceAuthorizationCallback().handle(
            request().withMethod("POST").withPath("/device_authorization").withBody("scope=" + enc("openid profile")));
        assertThat(response.getStatusCode(), is(200));

        JsonNode body = objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("device_code").asText(), containsString("mock-device-code-"));
        assertTrue("user_code present", body.has("user_code"));
        assertThat(body.get("verification_uri").asText(), is("https://idp.test/device"));
        assertThat(body.get("verification_uri_complete").asText(),
            containsString("https://idp.test/device?user_code="));
        assertTrue("expires_in present", body.get("expires_in").asLong() > 0);
        assertThat(body.get("interval").asInt(), is(5));
    }

    @Test
    public void deviceCodeGrantApprovesImmediatelyByDefault() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        String deviceCode = objectMapper.readTree(new OidcDeviceAuthorizationCallback().handle(
            request().withMethod("POST").withPath("/device_authorization").withBody("scope=openid")
        ).getBodyAsString()).get("device_code").asText();

        HttpResponse tokenResponse = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                + "&device_code=" + enc(deviceCode)));
        assertThat(tokenResponse.getStatusCode(), is(200));
        JsonNode tokens = objectMapper.readTree(tokenResponse.getBodyAsString());
        assertTrue(tokens.has("access_token"));
        assertTrue(tokens.has("id_token"));
        assertTrue("device grant returns a refresh_token", tokens.has("refresh_token"));
    }

    @Test
    public void deviceCodeGrantReturnsAuthorizationPendingThenTokens() throws Exception {
        // pendingPolls = 2 → first two polls pending, third mints tokens
        generator.generate(new OidcProviderConfiguration().setDeviceCodePendingPolls(2));

        String deviceCode = objectMapper.readTree(new OidcDeviceAuthorizationCallback().handle(
            request().withMethod("POST").withPath("/device_authorization").withBody("scope=openid")
        ).getBodyAsString()).get("device_code").asText();

        String body = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
            + "&device_code=" + enc(deviceCode);

        for (int i = 0; i < 2; i++) {
            HttpResponse pending = new OidcTokenCallback().handle(tokenRequest(body));
            assertThat("poll " + i + " should be pending", pending.getStatusCode(), is(400));
            assertThat(pending.getBodyAsString(), containsString("authorization_pending"));
        }

        HttpResponse minted = new OidcTokenCallback().handle(tokenRequest(body));
        assertThat(minted.getStatusCode(), is(200));
        assertTrue(objectMapper.readTree(minted.getBodyAsString()).has("access_token"));
    }

    @Test
    public void deviceCodeIsSingleUseAfterApproval() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        String deviceCode = objectMapper.readTree(new OidcDeviceAuthorizationCallback().handle(
            request().withMethod("POST").withPath("/device_authorization").withBody("scope=openid")
        ).getBodyAsString()).get("device_code").asText();

        String body = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
            + "&device_code=" + enc(deviceCode);

        assertThat(new OidcTokenCallback().handle(tokenRequest(body)).getStatusCode(), is(200));
        // second exchange of the same device code must fail (single-use after approval)
        HttpResponse second = new OidcTokenCallback().handle(tokenRequest(body));
        assertThat(second.getStatusCode(), is(400));
        assertThat(second.getBodyAsString(), containsString("expired_token"));
    }

    @Test
    public void unknownDeviceCodeIsRejected() {
        generator.generate(new OidcProviderConfiguration());

        HttpResponse tokenResponse = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                + "&device_code=never-issued"));
        assertThat(tokenResponse.getStatusCode(), is(400));
        assertThat(tokenResponse.getBodyAsString(), containsString("expired_token"));
    }

    @Test
    public void clientAuthBasicSucceedsWithCorrectCredentials() throws Exception {
        generator.generate(new OidcProviderConfiguration()
            .setEnforceClientAuthentication(true)
            .setClientId("the-client")
            .setClientSecret("the-secret"));

        String basic = Base64.getEncoder().encodeToString("the-client:the-secret".getBytes(StandardCharsets.UTF_8));
        HttpResponse response = new OidcTokenCallback().handle(
            request().withMethod("POST").withPath("/token")
                .withHeader("Authorization", "Basic " + basic)
                .withBody("grant_type=client_credentials"));
        assertThat(response.getStatusCode(), is(200));
        assertTrue(objectMapper.readTree(response.getBodyAsString()).has("access_token"));
    }

    @Test
    public void clientAuthPostSucceedsWithCorrectCredentials() throws Exception {
        generator.generate(new OidcProviderConfiguration()
            .setEnforceClientAuthentication(true)
            .setClientId("the-client")
            .setClientSecret("the-secret"));

        HttpResponse response = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=client_credentials&client_id=the-client&client_secret=the-secret"));
        assertThat(response.getStatusCode(), is(200));
        assertTrue(objectMapper.readTree(response.getBodyAsString()).has("access_token"));
    }

    @Test
    public void clientAuthMissingCredentialsReturns401InvalidClient() {
        generator.generate(new OidcProviderConfiguration()
            .setEnforceClientAuthentication(true)
            .setClientId("the-client")
            .setClientSecret("the-secret"));

        HttpResponse response = new OidcTokenCallback().handle(tokenRequest("grant_type=client_credentials"));
        assertThat(response.getStatusCode(), is(401));
        assertThat(response.getFirstHeader("WWW-Authenticate"), is("Basic"));
        assertThat(response.getBodyAsString(), containsString("invalid_client"));
    }

    @Test
    public void clientAuthWrongSecretReturns401InvalidClient() {
        generator.generate(new OidcProviderConfiguration()
            .setEnforceClientAuthentication(true)
            .setClientId("the-client")
            .setClientSecret("the-secret"));

        HttpResponse response = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=client_credentials&client_id=the-client&client_secret=WRONG"));
        assertThat(response.getStatusCode(), is(401));
        assertThat(response.getBodyAsString(), containsString("invalid_client"));
    }

    @Test
    public void clientAuthDisabledAllowsNoCredentials() throws Exception {
        // default flag off → no credentials still works (back-compat)
        generator.generate(new OidcProviderConfiguration());

        HttpResponse response = new OidcTokenCallback().handle(tokenRequest("grant_type=client_credentials"));
        assertThat(response.getStatusCode(), is(200));
        assertTrue(objectMapper.readTree(response.getBodyAsString()).has("access_token"));
    }

    @Test
    public void opaqueAccessTokenIsNotAJwtButIdTokenStillVerifies() throws Exception {
        List<Expectation> expectations = generator.generate(new OidcProviderConfiguration()
            .setOpaqueAccessToken(true));

        JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody());
        String accessToken = tokenResponse.get("access_token").asText();

        // opaque: not a 3-segment JWT
        assertThat("opaque access token is not a JWT", accessToken.split("\\.").length, is(1));
        assertThat(accessToken, containsString("mock-opaque-"));

        // id_token is still a signed JWT and verifies against the JWKS
        JWKSet jwkSet = JWKSet.parse(expectations.get(1).getHttpResponse().getBodyAsString());
        JWSVerifier verifier = new RSASSAVerifier((RSAKey) jwkSet.getKeys().get(0));
        SignedJWT idToken = SignedJWT.parse(tokenResponse.get("id_token").asText());
        assertTrue("id_token must still verify when access_token is opaque", idToken.verify(verifier));
    }

    @Test
    public void introspectionResolvesOpaqueAccessToken() throws Exception {
        generator.generate(new OidcProviderConfiguration()
            .setOpaqueAccessToken(true)
            .setSubject("opaque-sub"));

        String accessToken = objectMapper.readTree(tokenResponseBody()).get("access_token").asText();

        HttpResponse introspect = new OidcIntrospectionCallback().handle(
            request().withMethod("POST").withPath("/introspect").withBody("token=" + enc(accessToken)));
        JsonNode body = objectMapper.readTree(introspect.getBodyAsString());
        assertThat(body.get("active").asBoolean(), is(true));
        assertThat(body.get("sub").asText(), is("opaque-sub"));
    }

    @Test
    public void introspectionUnknownOpaqueTokenIsInactive() throws Exception {
        generator.generate(new OidcProviderConfiguration().setOpaqueAccessToken(true));

        HttpResponse introspect = new OidcIntrospectionCallback().handle(
            request().withMethod("POST").withPath("/introspect").withBody("token=never-issued-opaque"));
        JsonNode body = objectMapper.readTree(introspect.getBodyAsString());
        assertThat(body.get("active").asBoolean(), is(false));
    }

    @Test
    public void tokenErrorEnvelopeIncludesErrorUri() throws Exception {
        generator.generate(new OidcProviderConfiguration());

        HttpResponse response = new OidcTokenCallback().handle(tokenRequest(
            "grant_type=authorization_code&code=never-issued&redirect_uri=" + enc("https://app/cb")));
        JsonNode body = objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("error").asText(), is("invalid_grant"));
        assertTrue("RFC 6749 §5.2 error_description", body.has("error_description"));
        assertTrue("optional error_uri present", body.has("error_uri"));
    }

    // --- helpers ---

    private static HttpRequest tokenRequest(String body) {
        return request().withMethod("POST").withPath("/token").withBody(body);
    }

    private static String extractCode(String location) {
        assertThat("expected a redirect Location header", location, is(notNullValue()));
        for (String pair : location.substring(location.indexOf('?') + 1).split("&")) {
            if (pair.startsWith("code=")) {
                return pair.substring("code=".length());
            }
        }
        assertThat("location did not contain a code", location, is(nullValue()));
        return null;
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String s256(String codeVerifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    // --- secret fields are WRITE_ONLY: never serialized back out, but still deserialize from inbound PUT ---

    @Test
    public void clientSecretAndPrivateKeyAreNeverSerialized() throws Exception {
        OidcProviderConfiguration config = new OidcProviderConfiguration()
            .setClientSecret("SUPER-SECRET-CLIENT")
            .setPrivateKeyPem("-----BEGIN PRIVATE KEY-----\nSUPER-SECRET-KEY\n-----END PRIVATE KEY-----");

        String json = new ObjectMapper().writeValueAsString(config);

        assertThat("client secret must not appear in serialized JSON", json, not(containsString("SUPER-SECRET-CLIENT")));
        assertThat(json, not(containsString("clientSecret")));
        assertThat("private key PEM must not appear in serialized JSON", json, not(containsString("SUPER-SECRET-KEY")));
        assertThat(json, not(containsString("privateKeyPem")));
        // non-secret fields are still serialized
        assertThat(json, containsString("clientId"));
    }

    @Test
    public void clientSecretAndPrivateKeyStillDeserializeFromInboundPut() throws Exception {
        String inbound = "{\"clientSecret\":\"inbound-secret\","
            + "\"privateKeyPem\":\"-----BEGIN PRIVATE KEY-----\\nKEYDATA\\n-----END PRIVATE KEY-----\"}";

        OidcProviderConfiguration config = new ObjectMapper().readValue(inbound, OidcProviderConfiguration.class);

        assertThat(config.getClientSecret(), is("inbound-secret"));
        assertThat(config.getPrivateKeyPem(), containsString("KEYDATA"));
    }
}
