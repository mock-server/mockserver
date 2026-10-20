package org.mockserver.cors;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.cors.CORSOriginPolicy.isControlPlaneOriginAllowed;
import static org.mockserver.cors.CORSOriginPolicy.isDefaultAllowedOrigin;

public class CORSOriginPolicyTest {

    private static final String HOST = "mockserver.internal:1080";

    @Test
    public void shouldAllowLoopbackOriginsOverHttpOrHttpsOnAnyPort() {
        assertThat(isDefaultAllowedOrigin("http://localhost", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("http://localhost:3000", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("HTTPS://LOCALHOST:8443", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("http://127.0.0.1:5173", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("https://127.0.0.1", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("http://[::1]:1080", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("http://localhost:3000", null), is(true));
    }

    @Test
    public void shouldRejectALoopbackHostUnderAnotherScheme() {
        assertThat(isDefaultAllowedOrigin("chrome-extension://localhost", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("ws://localhost:1080", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("file://localhost", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("ftp://127.0.0.1", HOST), is(false));
    }

    @Test
    public void shouldAllowAnOriginMatchingTheHostHeader() {
        assertThat(isDefaultAllowedOrigin("http://mockserver.internal:1080", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("https://MockServer.Internal:1080", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("http://mockserver.internal", "mockserver.internal"), is(true));
        assertThat(isDefaultAllowedOrigin("http://mockserver.internal", "mockserver.internal:80"), is(true));
        assertThat(isDefaultAllowedOrigin("https://mockserver.internal", "mockserver.internal:443"), is(true));
        assertThat(isDefaultAllowedOrigin("http://10.0.0.5:1080", "10.0.0.5:1080"), is(true));
        assertThat(isDefaultAllowedOrigin("http://[fd00::5]:1080", "[fd00::5]:1080"), is(true));
        assertThat(isDefaultAllowedOrigin("http://project_mockserver_1:1080", "project_mockserver_1:1080"), is(true));
    }

    @Test
    public void shouldRejectAnOriginWhoseHostOrPortDiffersFromTheHostHeader() {
        assertThat(isDefaultAllowedOrigin("http://mockserver.internal:1081", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://mockserver.internal", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("https://mockserver.internal", "mockserver.internal:80"), is(false));
        assertThat(isDefaultAllowedOrigin("http://other.internal:1080", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://mockserver.internal.attacker.example:1080", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://0.0.0.0:1080", HOST), is(false));
    }

    @Test
    public void shouldRejectForeignOrigins() {
        assertThat(isDefaultAllowedOrigin("https://attacker.example", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://attacker.example", null), is(false));
        assertThat(isDefaultAllowedOrigin("http://attacker.example", ""), is(false));
    }

    @Test
    public void shouldRejectOriginsThatOnlyLookLikeLoopback() {
        String[] lookalikes = {
            "http://localhost.attacker.example",
            "http://127.0.0.1.attacker.example",
            "http://127.0.0.1.nip.io",
            "http://localhost@attacker.example",
            "http://attacker.example@localhost",
            "http://attacker.example#localhost",
            "http://attacker.example/localhost",
            "http://attacker.example?localhost",
            "http://localhost%2eattacker.example",
            "http://localhost.",
            "http://[::ffff:127.0.0.1]",
            "http://[::ffff:7f00:1]",
            "http://0.0.0.0",
            "http://2130706433",
            "http://0177.0.0.1",
            "http://127.1",
            "http://localhost:",
            "http://localhost:0",
            "http://localhost:99999",
            "http://localhost:3000, https://attacker.example",
        };
        for (String origin : lookalikes) {
            assertThat(origin, isDefaultAllowedOrigin(origin, HOST), is(false));
        }
    }

    @Test
    public void shouldRejectNullMissingAndMalformedOrigins() {
        assertThat(isDefaultAllowedOrigin("null", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("null", "null"), is(false));
        assertThat(isDefaultAllowedOrigin(null, HOST), is(false));
        assertThat(isDefaultAllowedOrigin("", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("localhost:3000", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("file://", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://exa mple", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://mockserver.internal:1080/", HOST), is(false));
    }

    @Test
    public void shouldNotTreatAHostHeaderWithUserInfoOrPathAsSameHost() {
        assertThat(isDefaultAllowedOrigin("http://attacker.example:1080", "victim@attacker.example:1080"), is(false));
        assertThat(isDefaultAllowedOrigin("http://attacker.example:1080", "attacker.example:1080/path"), is(false));
    }

    @Test
    public void shouldAllowAMissingOrigin() {
        assertThat(isControlPlaneOriginAllowed("", null, HOST), is(true));
        assertThat(isControlPlaneOriginAllowed("https://app.example.com", "", HOST), is(true));
    }

    @Test
    public void shouldApplyTheLoopbackDefaultWhenTheAllowListIsBlank() {
        assertThat(isControlPlaneOriginAllowed("", "http://localhost:3000", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(null, "http://mockserver.internal:1080", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(" ", "https://attacker.example", HOST), is(false));
        assertThat(isControlPlaneOriginAllowed("", "null", HOST), is(false));
    }

    @Test
    public void shouldTreatAConfiguredValueAsAnAllowList() {
        String allowList = "https://app.example.com, http://ci-browser.internal:8080";
        assertThat(isControlPlaneOriginAllowed(allowList, "https://app.example.com", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(allowList, "HTTP://CI-BROWSER.INTERNAL:8080", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(allowList, "https://attacker.example", HOST), is(false));
        assertThat(isControlPlaneOriginAllowed(allowList, "https://app.example.com.attacker.example", HOST), is(false));
        assertThat("loopback is only the default, not part of an explicit list",
            isControlPlaneOriginAllowed(allowList, "http://localhost:3000", HOST), is(false));
        assertThat("a page MockServer served itself is always allowed",
            isControlPlaneOriginAllowed(allowList, "http://mockserver.internal:1080", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(allowList, "null", HOST), is(false));
    }

    @Test
    public void shouldAllowAnyOriginForAWildcardEntry() {
        assertThat(isControlPlaneOriginAllowed("*", "https://attacker.example", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed("https://app.example.com,*", "null", HOST), is(true));
    }
}
