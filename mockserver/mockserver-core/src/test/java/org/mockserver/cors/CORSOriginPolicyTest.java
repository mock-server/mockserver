package org.mockserver.cors;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.cors.CORSOriginPolicy.consistentHost;
import static org.mockserver.cors.CORSOriginPolicy.isControlPlaneOriginAllowed;
import static org.mockserver.cors.CORSOriginPolicy.isDefaultAllowedOrigin;
import static org.mockserver.cors.CORSOriginPolicy.trustedHostSuffixErrors;
import static org.mockserver.cors.CORSOriginPolicy.trustedHostSuffixWarnings;

public class CORSOriginPolicyTest {

    private static final String HOST = "mockserver:1080";

    @Test
    public void shouldAllowLoopbackOriginsOverHttpOrHttpsOnAnyPort() {
        assertThat(isDefaultAllowedOrigin("http://localhost", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("http://localhost:3000", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("HTTPS://LOCALHOST:8443", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("http://127.0.0.1:5173", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("https://127.0.0.1", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("http://[::1]:1080", HOST), is(true));
        assertThat(isDefaultAllowedOrigin("http://localhost:3000", null), is(true));
        assertThat(isDefaultAllowedOrigin("http://localhost:3000", "evil.example.com:1080"), is(true));
    }

    @Test
    public void shouldRejectALoopbackHostUnderAnotherScheme() {
        assertThat(isDefaultAllowedOrigin("chrome-extension://localhost", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("ws://localhost:1080", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("file://localhost", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("ftp://127.0.0.1", HOST), is(false));
    }

    @Test
    public void shouldAllowASameHostOriginOnARebindingSafeHost() {
        String[][] hostAndOrigin = {
            {"localhost:1080", "http://localhost:1080"},
            {"LOCALHOST.:1080", "http://localhost.:1080"},
            {"10.0.0.5:1080", "http://10.0.0.5:1080"},
            {"0.0.0.0:1080", "http://0.0.0.0:1080"},
            {"[fd00::5]:1080", "http://[fd00::5]:1080"},
            {"[::ffff:10.0.0.5]:1080", "http://[::ffff:10.0.0.5]:1080"},
            {"mockserver:1080", "http://mockserver:1080"},
            {"MockServer:1080", "https://mockserver:1080"},
            {"project_mockserver_1:1080", "http://project_mockserver_1:1080"},
            {"xn--mckserver-m4a:1080", "http://xn--mckserver-m4a:1080"},
            {"mockserver", "http://mockserver"},
            {"mockserver:80", "http://mockserver"},
            {"mockserver:443", "https://mockserver"},
            {"mockserver.default.svc:1080", "http://mockserver.default.svc:1080"},
            {"mockserver.default.svc.cluster.local:1080", "http://mockserver.default.svc.cluster.local:1080"},
            {"mockserver.default.svc.cluster.local.:1080", "http://mockserver.default.svc.cluster.local.:1080"},
            {"MockServer.Default.SVC.Cluster.Local:1080", "https://mockserver.default.svc.cluster.local:1080"},
            {"host.docker.internal:1080", "http://host.docker.internal:1080"},
            {"ip-10-0-0-1.ec2.internal:1080", "http://ip-10-0-0-1.ec2.internal:1080"},
            {"mockserver.internal.:1080", "http://mockserver.internal.:1080"},
        };
        for (String[] pair : hostAndOrigin) {
            assertThat(pair[1] + " with Host " + pair[0], isDefaultAllowedOrigin(pair[1], pair[0]), is(true));
        }
    }

    @Test
    public void shouldRefuseASameHostOriginOnANameDnsRebindingCanTakeOver() {
        String[] hosts = {
            "evil.example.com:1080",
            "mockserver.example:1080",
            "internal.evil.com:1080",
            "mockserver.internal.evil.com:1080",
            "mockserverinternal.com:1080",
            "localhost.evil.com:1080",
            "127.0.0.1.nip.io:1080",
            "svc.evil.com:1080",
            "svc.cluster.local:1080",
            "evil.com.svc.cluster.local.evil.com:1080",
            "mockserver.svc.evil.com:1080",
            "evilsvc:1080.example.com",
            "mockserver.svc.cluster.localhost:1080",
            "1.2.3.4.evil.com:1080",
            "1.2.3:1080",
            "1.2.3.256:1080",
            "0x7f.0.0.1:1080",
            "xn--e1afmkfd.xn--p1ai:1080",
        };
        for (String host : hosts) {
            assertThat(host, isDefaultAllowedOrigin("http://" + host, host), is(false));
        }
    }

    @Test
    public void shouldRefuseTheDnsRebindingCase() {
        assertThat(isControlPlaneOriginAllowed("", "", "http://evil.example.com:1080", "evil.example.com:1080"), is(false));
        assertThat("a configured allow-list does not reopen the same-host rule for public names",
            isControlPlaneOriginAllowed("https://app.example.com", "", "http://evil.example.com:1080", "evil.example.com:1080"), is(false));
    }

    @Test
    public void shouldRefuseANonAsciiHostName() {
        String[] hosts = {"m\u00f6ckserver:1080", "mockserver.\uff53vc:1080", "mockserver.default.\u0455vc:1080", "\u212aube:1080"};
        for (String host : hosts) {
            assertThat(host, isDefaultAllowedOrigin("http://" + host, host), is(false));
        }
    }

    @Test
    public void shouldAllowASameHostOriginOnAConfiguredSuffix() {
        String suffixes = "k8s.example, .corp.example.";
        assertThat(isControlPlaneOriginAllowed("", suffixes, "http://mockserver.ns.svc.k8s.example:1080", "mockserver.ns.svc.k8s.example:1080"), is(true));
        assertThat(isControlPlaneOriginAllowed("", suffixes, "http://mockserver.corp.example:1080", "MockServer.Corp.Example.:1080"), is(false));
        assertThat(isControlPlaneOriginAllowed("", suffixes, "http://mockserver.corp.example.:1080", "mockserver.corp.example.:1080"), is(true));
        assertThat(isControlPlaneOriginAllowed("", suffixes, "http://mockserver.corp.example:1080", "mockserver.corp.example:1080"), is(true));
        assertThat("the suffix itself is not a host under it",
            isControlPlaneOriginAllowed("", suffixes, "http://k8s.example:1080", "k8s.example:1080"), is(false));
        assertThat(isControlPlaneOriginAllowed("", suffixes, "http://evilk8s.example:1080", "evilk8s.example:1080"), is(false));
        assertThat(isControlPlaneOriginAllowed("", suffixes, "http://k8s.example.evil.com:1080", "k8s.example.evil.com:1080"), is(false));
        assertThat(isControlPlaneOriginAllowed("", "", "http://mockserver.ns.svc.k8s.example:1080", "mockserver.ns.svc.k8s.example:1080"), is(false));
        assertThat("blank and malformed entries trust nothing",
            isControlPlaneOriginAllowed("", " , ., ..,*", "http://evil.example.com:1080", "evil.example.com:1080"), is(false));
    }

    @Test
    public void shouldAcceptAWildcardFormOfAConfiguredSuffix() {
        assertThat(isControlPlaneOriginAllowed("", "*.corp.example", "http://mockserver.corp.example:1080", "mockserver.corp.example:1080"), is(true));
        assertThat(isControlPlaneOriginAllowed("", "*.corp.example", "http://corp.example:1080", "corp.example:1080"), is(false));
        assertThat(isControlPlaneOriginAllowed("", "*corp.example", "http://evilcorp.example:1080", "evilcorp.example:1080"), is(false));
    }

    @Test
    public void shouldWarnAboutUnusableOrWholeTopLevelDomainSuffixes() {
        assertThat(trustedHostSuffixWarnings(""), is(empty()));
        assertThat(trustedHostSuffixWarnings(null), is(empty()));
        assertThat(trustedHostSuffixWarnings("corp.example, .k8s.example., *.svc.example, ,"), is(empty()));

        List<String> warnings = trustedHostSuffixWarnings("*, corp..example, *corp.example, http://corp.example, \u212Aube.example, corp");

        assertThat(warnings.size(), is(6));
        assertThat(warnings.get(0), containsString("\"*\" is not a host-name suffix"));
        assertThat("a KELVIN SIGN is not lower-cased into an ASCII k", warnings.get(4), containsString("is not a host-name suffix"));
        assertThat(warnings.get(5), containsString("\"corp\" is a whole top-level domain"));
        assertThat(isControlPlaneOriginAllowed("", "corp", "http://mockserver.corp:1080", "mockserver.corp:1080"), is(true));
    }

    @Test
    public void shouldRejectPublicSuffixes() {
        String publicSuffixes = "com, .com., *.com, co.uk, *.co.uk, github.io";
        List<String> errors = trustedHostSuffixErrors(publicSuffixes);

        assertThat(errors.size(), is(6));
        assertThat(errors.get(3), containsString("\"co.uk\" is REJECTED because it is a public suffix"));
        assertThat(trustedHostSuffixWarnings(publicSuffixes), is(empty()));
        for (String host : new String[]{"evil.com:1080", "evil.co.uk:1080", "evil.github.io:1080"}) {
            assertThat(host, isControlPlaneOriginAllowed("", publicSuffixes, "http://" + host, host), is(false));
        }
    }

    @Test
    public void shouldNotRejectPrivateSuffixesThatArePublicSuffixLookalikes() {
        String privateSuffixes = "home.arpa, internal, svc.cluster.local, corp.example, example.co.uk";

        assertThat(trustedHostSuffixErrors(privateSuffixes), is(empty()));
        assertThat(trustedHostSuffixErrors(""), is(empty()));
        assertThat(trustedHostSuffixErrors(null), is(empty()));
        assertThat(isControlPlaneOriginAllowed("", privateSuffixes, "http://mockserver.home.arpa:1080", "mockserver.home.arpa:1080"), is(true));
        assertThat(isControlPlaneOriginAllowed("", privateSuffixes, "http://mockserver.example.co.uk:1080", "mockserver.example.co.uk:1080"), is(true));
    }

    @Test
    public void shouldAllowAPublicSameHostNameOnlyWhenItsOriginIsListed() {
        assertThat(isControlPlaneOriginAllowed("http://mockserver.example.com:1080", "", "http://mockserver.example.com:1080", "mockserver.example.com:1080"), is(true));
        assertThat(isControlPlaneOriginAllowed("http://mockserver.example.com:1080", "", "http://evil.example.com:1080", "evil.example.com:1080"), is(false));
    }

    @Test
    public void shouldRejectAnOriginWhoseHostOrPortDiffersFromTheHostHeader() {
        assertThat(isDefaultAllowedOrigin("http://mockserver:1081", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://mockserver", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("https://mockserver", "mockserver:80"), is(false));
        assertThat(isDefaultAllowedOrigin("http://other:1080", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://mockserver.attacker.example:1080", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://0.0.0.0:1080", HOST), is(false));
        assertThat(isDefaultAllowedOrigin("http://mockserver.:1080", HOST), is(false));
    }

    @Test
    public void shouldNotTrustAHostThatDisagreesWithAnotherHostValue() {
        assertThat(consistentHost(Arrays.asList("mockserver:1080", "evil.example.com:1080")), is(nullValue()));
        assertThat(consistentHost(Arrays.asList("evil.example.com:1080", "mockserver:1080")), is(nullValue()));
        assertThat(consistentHost(Arrays.asList("mockserver:1080", "MockServer:1080 ")), is("mockserver:1080"));
        assertThat(consistentHost(Collections.singletonList("mockserver:1080")), is("mockserver:1080"));
        assertThat(consistentHost(Collections.emptyList()), is(nullValue()));
        assertThat(consistentHost(null), is(nullValue()));
        assertThat(isControlPlaneOriginAllowed(configuration(), "http://mockserver:1080", Arrays.asList("mockserver:1080", "evil.example.com:1080")), is(false));
        assertThat(isControlPlaneOriginAllowed(configuration(), "http://mockserver:1080", Arrays.asList("mockserver:1080", "mockserver:1080")), is(true));
    }

    @Test
    public void shouldReadBothPropertiesFromTheConfiguration() {
        assertThat(isControlPlaneOriginAllowed(configuration().controlPlaneTrustedHostSuffixes("corp.example"),
            "http://mockserver.corp.example:1080", Collections.singletonList("mockserver.corp.example:1080")), is(true));
        assertThat(isControlPlaneOriginAllowed(configuration().controlPlaneCorsAllowOrigin("https://app.example.com"),
            "https://app.example.com", Collections.emptyList()), is(true));
        assertThat(isControlPlaneOriginAllowed((org.mockserver.configuration.Configuration) null,
            "http://localhost:3000", Collections.emptyList()), is(true));
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
        assertThat(isControlPlaneOriginAllowed("", "", null, HOST), is(true));
        assertThat(isControlPlaneOriginAllowed("https://app.example.com", "", "", HOST), is(true));
    }

    @Test
    public void shouldApplyTheLoopbackDefaultWhenTheAllowListIsBlank() {
        assertThat(isControlPlaneOriginAllowed("", "", "http://localhost:3000", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(null, "", "http://mockserver:1080", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(" ", "", "https://attacker.example", HOST), is(false));
        assertThat(isControlPlaneOriginAllowed("", "", "null", HOST), is(false));
    }

    @Test
    public void shouldTreatAConfiguredValueAsAnAllowList() {
        String allowList = "https://app.example.com, http://ci-browser.internal:8080";
        assertThat(isControlPlaneOriginAllowed(allowList, "", "https://app.example.com", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(allowList, "", "HTTP://CI-BROWSER.INTERNAL:8080", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(allowList, "", "https://attacker.example", HOST), is(false));
        assertThat(isControlPlaneOriginAllowed(allowList, "", "https://app.example.com.attacker.example", HOST), is(false));
        assertThat("loopback is only the default, not part of an explicit list",
            isControlPlaneOriginAllowed(allowList, "", "http://localhost:3000", HOST), is(false));
        assertThat("a page MockServer served itself on a rebinding-safe host is allowed",
            isControlPlaneOriginAllowed(allowList, "", "http://mockserver:1080", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed(allowList, "", "null", HOST), is(false));
    }

    @Test
    public void shouldAllowAnyOriginForAWildcardEntry() {
        assertThat(isControlPlaneOriginAllowed("*", "", "https://attacker.example", HOST), is(true));
        assertThat(isControlPlaneOriginAllowed("https://app.example.com,*", "", "null", HOST), is(true));
    }
}
