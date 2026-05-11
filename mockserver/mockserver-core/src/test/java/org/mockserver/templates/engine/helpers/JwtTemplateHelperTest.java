package org.mockserver.templates.engine.helpers;

import com.google.common.collect.ImmutableMap;
import com.nimbusds.jwt.SignedJWT;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class JwtTemplateHelperTest {

    @Test
    public void shouldGenerateDefaultJWT() throws Exception {
        // given
        JwtTemplateHelper helper = new JwtTemplateHelper();

        // when
        String jwt = helper.generate();

        // then
        assertThat(jwt, is(notNullValue()));
        SignedJWT signedJWT = SignedJWT.parse(jwt);
        assertThat(signedJWT.getJWTClaimsSet().getSubject(), is(notNullValue()));
        assertThat(signedJWT.getJWTClaimsSet().getIssuer(), is("https://www.mock-server.com"));
        assertThat(signedJWT.getJWTClaimsSet().getAudience(), contains("https://www.mock-server.com"));
    }

    @Test
    public void shouldGenerateJWTWithCustomClaims() throws Exception {
        // given
        JwtTemplateHelper helper = new JwtTemplateHelper();
        Map<String, Object> claims = ImmutableMap.of(
            "sub", "test-subject",
            "iss", "https://test.example.com",
            "scope", "read write"
        );

        // when
        String jwt = helper.generate(claims);

        // then
        assertThat(jwt, is(notNullValue()));
        SignedJWT signedJWT = SignedJWT.parse(jwt);
        assertThat(signedJWT.getJWTClaimsSet().getSubject(), is("test-subject"));
        assertThat(signedJWT.getJWTClaimsSet().getIssuer(), is("https://test.example.com"));
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("scope"), is("read write"));
    }

    @Test
    public void shouldGenerateJWKS() {
        // given
        JwtTemplateHelper helper = new JwtTemplateHelper();

        // when
        String jwks = helper.jwks();

        // then
        assertThat(jwks, is(notNullValue()));
        assertThat(jwks, containsString("keys"));
        assertThat(jwks, containsString("kty"));
        assertThat(jwks, containsString("RSA"));
    }

    @Test
    public void shouldReturnJWTFromToString() throws Exception {
        // given
        JwtTemplateHelper helper = new JwtTemplateHelper();

        // when
        String result = helper.toString();

        // then
        assertThat(result, is(notNullValue()));
        SignedJWT signedJWT = SignedJWT.parse(result);
        assertThat(signedJWT.getJWTClaimsSet().getSubject(), is(notNullValue()));
    }

    @Test
    public void shouldGenerateConsistentJWKSForSameHelper() {
        // given
        JwtTemplateHelper helper = new JwtTemplateHelper();

        // when
        String jwks1 = helper.jwks();
        String jwks2 = helper.jwks();

        // then
        assertThat(jwks1, is(equalTo(jwks2)));
    }

    @Test
    public void shouldBeRegisteredInTemplateFunctions() {
        // given / when
        Object jwtHelper = org.mockserver.templates.engine.TemplateFunctions.BUILT_IN_HELPERS.get("jwt");

        // then
        assertThat(jwtHelper, is(notNullValue()));
        assertThat(jwtHelper, instanceOf(JwtTemplateHelper.class));
    }
}
