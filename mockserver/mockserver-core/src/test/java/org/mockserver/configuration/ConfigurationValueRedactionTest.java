package org.mockserver.configuration;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.mockserver.configuration.ConfigurationProperties.REDACTED_VALUE;
import static org.mockserver.configuration.ConfigurationProperties.redactSensitiveValue;
import static org.mockserver.configuration.ConfigurationProperties.restoreRedactedValue;

/**
 * The single redaction rule shared by every surface that discloses a configuration value:
 * {@code GET /mockserver/configuration} (via {@code ConfigurationDTO}), {@code GET /mockserver/config}
 * and {@code --print-config} (via {@code effectiveConfiguration()}), and the startup property-file log
 * dump — which calls exactly this and nothing else, so these cases are what that dump prints.
 *
 * <p>Pure except for {@code warnsWhenTheMaskWasEditedAroundAndStaysSilentOnAnUntouchedRoundTrip},
 * which attaches a {@code java.util.logging} handler to a shared logger. That is safe in the
 * parallel Surefire phase for three reasons, all of which must hold: JUL's handler list is
 * copy-on-write, the handler filters on a property name used nowhere else in the repository, and
 * both the handler and the original level are removed in a {@code finally}. If a later edit drops
 * the unique name or the {@code finally}, this class stops being parallel-safe.
 */
public class ConfigurationValueRedactionTest {

    @Test
    public void masksAWholeValueCredentialByPropertyName() {
        assertThat(redactSensitiveValue("mockserver.llmApiKey", "sk-secret"), is(REDACTED_VALUE));
        assertThat(redactSensitiveValue("llmApiKey", "sk-secret"), is(REDACTED_VALUE));
        assertThat(redactSensitiveValue("mockserver.prometheusRemoteWriteBearerToken", "t"), is(REDACTED_VALUE));
    }

    @Test
    public void redactsOnlyTheCredentialHeadersOfAHeaderListProperty() {
        assertThat(redactSensitiveValue("mockserver.prometheusRemoteWriteHeaders",
                "Api-Key=NRAK-secret,X-Scope-OrgID=tenant-a"),
            is("Api-Key=" + REDACTED_VALUE + ",X-Scope-OrgID=tenant-a"));
        assertThat(redactSensitiveValue("mockserver.prometheusRemoteWriteHeaders",
                "Authorization=Bearer secret,X-Custom=abc"),
            is("Authorization=" + REDACTED_VALUE + ",X-Custom=abc"));
    }

    @Test
    public void leavesAHeaderListWithoutCredentialsByteIdentical() {
        String headers = "X-Scope-OrgID=tenant-a, X-Custom=abc";
        assertThat(redactSensitiveValue("mockserver.prometheusRemoteWriteHeaders", headers), is(headers));
    }

    @Test
    public void leavesABackendsFilePathUnchangedButRedactsAnInlineDocument() {
        // the DOCUMENTED shape is a path — the secrets live in the file, which no endpoint returns
        assertThat(redactSensitiveValue("mockserver.llmBackendsConfig", "/etc/mockserver/backends.json"),
            is("/etc/mockserver/backends.json"));
        assertThat(redactSensitiveValue("mockserver.llmBackendsConfig",
                "[{\"name\":\"openai\",\"apiKey\":\"sk-secret\"}]"),
            is("[{\"name\":\"openai\",\"apiKey\":\"" + REDACTED_VALUE + "\"}]"));
    }

    @Test
    public void failsClosedOnAnUnparseableDocumentAndOnNonStringSecrets() {
        assertThat(redactSensitiveValue("mockserver.llmBackendsConfig", "[{\"apiKey\":\"sk-secret\""),
            is(REDACTED_VALUE));
        assertThat(redactSensitiveValue("mockserver.llmBackendsConfig", "{\"tokens\":[\"a-secret\",\"b-secret\"]}"),
            not(containsString("secret")));
        assertThat(redactSensitiveValue("mockserver.llmBackendsConfig", "{\"apiKey\":9876543210}"),
            not(containsString("9876543210")));
    }

    @Test
    public void treatsTheAbbreviatedCredentialNamePasswdAsSensitive() {
        // "passwd" is not a substring of "password", and it does not end in "key", so neither existing
        // rule reached it — a property or header named this way was disclosed in clear
        assertThat(redactSensitiveValue("mockserver.upstreamPasswd", "hunter2"), is(REDACTED_VALUE));
        assertThat(redactSensitiveValue("mockserver.prometheusRemoteWriteHeaders",
                "X-Passwd=hunter2,X-Scope-OrgID=tenant-a"),
            is("X-Passwd=" + REDACTED_VALUE + ",X-Scope-OrgID=tenant-a"));
        assertThat(redactSensitiveValue("mockserver.llmBackendsConfig", "{\"passwd\":\"hunter2\"}"),
            is("{\"passwd\":\"" + REDACTED_VALUE + "\"}"));
    }

    @Test
    public void masksAValueWhoseSECONDJsonDocumentCarriesTheSecret() {
        // Jackson parses the FIRST document and discards the rest unless FAIL_ON_TRAILING_TOKENS is
        // enabled. With it off, a first document holding no credential-named field meant "nothing was
        // redacted", so the ENTIRE original string — trailing document and all — was returned in clear
        // each of these parses as a COMPLETE first document with the secret in what FOLLOWS it, which
        // is what makes them discriminating: wrapping them in an extra [...] would make them malformed
        // regardless of the flag, so they would pass without it and prove nothing. Collected rather
        // than asserted one by one so a regression names every form that leaks, not just the first.
        List<String> leaked = new ArrayList<>();
        for (String value : new String[]{
            "{\"a\":1}{\"apiKey\":\"sk-LEAK0\"}",
            "{\"a\":1}{\"name\":\"o\",\"apiKey\":\"sk-LEAK1\"}",
            "{\"version\":1}\n[{\"name\":\"o\",\"apiKey\":\"sk-LEAK2\"}]",
            "[{\"a\":1}] [{\"apiKey\":\"sk-LEAK3\"}]"
        }) {
            String redacted = redactSensitiveValue("mockserver.llmBackendsConfig", value);
            if (redacted.contains("sk-LEAK")) {
                leaked.add(value + " -> " + redacted);
            }
        }

        assertThat("values whose trailing document was parsed away and then returned in clear: " + leaked,
            leaked, is(empty()));
        assertThat("and such a value is masked WHOLE, not partially",
            redactSensitiveValue("mockserver.llmBackendsConfig", "{\"a\":1}{\"apiKey\":\"sk-x\"}"),
            is(REDACTED_VALUE));
    }

    @Test
    public void treatsCredentialBearingHeaderNamesTheSubstringRulesMissAsSensitive() {
        for (String header : new String[]{
            "X-Signature", "X-Hub-Signature-256", "X-Hmac", "Authentication", "X-JWT",
            "X-Session-Id", "X-Otp", "X-Auth", "pwd", "salt", "X-Bearer"
        }) {
            assertThat(header + " carries a credential and must be masked",
                redactSensitiveValue("mockserver.prometheusRemoteWriteHeaders",
                    header + "=s3cret,X-Scope-OrgID=tenant-a"),
                is(header + "=" + REDACTED_VALUE + ",X-Scope-OrgID=tenant-a"));
        }
    }

    @Test
    public void keepsNonSecretAuthenticationAndJwtPropertyNamesReadable() {
        // these words are credential-bearing as HEADER names but describe ordinary settings as PROPERTY
        // names, so they live in the header/field rule only — masking them would hide the very
        // configuration an operator debugging authentication needs to read
        assertThat(redactSensitiveValue("mockserver.tlsMutualAuthenticationRequired", "true"), is("true"));
        assertThat(redactSensitiveValue("mockserver.proxyAuthenticationUsername", "admin"), is("admin"));
        assertThat(redactSensitiveValue("mockserver.dataPlaneBasicAuthenticationRealm", "MockServer"),
            is("MockServer"));
        assertThat(redactSensitiveValue("mockserver.controlPlaneJWTAuthenticationRequired", "false"),
            is("false"));
        assertThat(redactSensitiveValue("mockserver.controlPlaneJWTAuthenticationJWKSource",
            "https://issuer/.well-known/jwks.json"), is("https://issuer/.well-known/jwks.json"));
    }

    @Test
    public void masksAValueThatEmbedsAJsonDocumentWithoutStartingWithOne() {
        // the detection used to require a leading { or [, so a JSON document behind a prefix — or behind
        // a byte-order mark, which String.trim() does not strip — was disclosed in clear
        assertThat(redactSensitiveValue("mockserver.llmBackendsConfig",
                "backends=[{\"name\":\"openai\",\"apiKey\":\"sk-leak\"}]"),
            not(containsString("sk-leak")));
        assertThat(redactSensitiveValue("mockserver.llmBackendsConfig",
                "\uFEFF[{\"name\":\"openai\",\"apiKey\":\"sk-leak\"}]"),
            not(containsString("sk-leak")));
    }

    @Test
    public void stillLeavesTheDocumentedBackendsFilePathShapeUntouched() {
        // the widened detection must not swallow the DOCUMENTED shape of llmBackendsConfig — a path
        for (String path : new String[]{
            "/etc/mockserver/backends.json",
            "./config/llm-backends.json",
            "C:\\mockserver\\backends.json",
            "file:/etc/mockserver/backends.json"
        }) {
            assertThat(redactSensitiveValue("mockserver.llmBackendsConfig", path), is(path));
        }
    }

    @Test
    public void leavesUnrelatedPropertiesAndAbsentValuesAlone() {
        assertThat(redactSensitiveValue("mockserver.maxExpectations", "4242"), is("4242"));
        assertThat(redactSensitiveValue("mockserver.prometheusRemoteWriteHeaders", ""), is(""));
        assertThat(redactSensitiveValue("mockserver.prometheusRemoteWriteHeaders", null), is((String) null));
    }

    @Test
    public void restoreIsTheInverseForTheValueTheRuleRedacted() {
        String held = "Api-Key=NRAK-secret,X-Scope-OrgID=tenant-a";
        String masked = redactSensitiveValue("mockserver.prometheusRemoteWriteHeaders", held);

        assertThat("an unedited round trip returns the held value verbatim",
            restoreRedactedValue("mockserver.prometheusRemoteWriteHeaders", masked, held), is(held));
        assertThat("a property with no embedded-credential shape is passed straight through",
            restoreRedactedValue("mockserver.maxExpectations", "4242", "1"), is("4242"));
    }

    @Test
    public void neverResolvesAnUnresolvableMaskInAStructuredValue() {
        // refused by each restore* method's own check — null means "leave the held value untouched"
        assertThat("a header whose value merely contains the mask cannot be resolved",
            restoreRedactedValue("mockserver.prometheusRemoteWriteHeaders",
                "Authorization=Bearer " + REDACTED_VALUE, "Authorization=Bearer real"), is((String) null));
        assertThat("a backend field whose value merely contains the mask cannot be resolved",
            restoreRedactedValue("mockserver.llmBackendsConfig",
                "[{\"name\":\"openai\",\"apiKey\":\"sk-" + REDACTED_VALUE + "\"}]",
                "[{\"name\":\"openai\",\"apiKey\":\"sk-real\"}]"), is((String) null));
    }

    @Test
    public void neverRestoresAHeldBackendCredentialIntoABackendWithANewDestination() {
        String held = "[{\"name\":\"openai\",\"provider\":\"OPENAI\",\"baseUrl\":\"https://held.example\",\"apiKey\":\"sk-real\","
            + "\"headers\":{\"Authorization\":\"Bearer real\"}}]";
        assertThat("a masked key is not carried to a new baseUrl",
            restoreRedactedValue("mockserver.llmBackendsConfig",
                "[{\"name\":\"openai\",\"provider\":\"OPENAI\",\"baseUrl\":\"https://attacker.example\",\"apiKey\":\"" + REDACTED_VALUE + "\"}]",
                held), is((String) null));
        assertThat("a masked key is not carried to a new provider",
            restoreRedactedValue("mockserver.llmBackendsConfig",
                "[{\"name\":\"openai\",\"provider\":\"ANTHROPIC\",\"baseUrl\":\"https://held.example\",\"apiKey\":\"" + REDACTED_VALUE + "\"}]",
                held), is((String) null));
        assertThat("a masked header nested in the backend is not carried to a new baseUrl either",
            restoreRedactedValue("mockserver.llmBackendsConfig",
                "[{\"name\":\"openai\",\"provider\":\"OPENAI\",\"baseUrl\":\"https://attacker.example\",\"apiKey\":\"sk-new\","
                    + "\"headers\":{\"Authorization\":\"" + REDACTED_VALUE + "\"}}]",
                held), is((String) null));
        assertThat("a new destination with a new key is applied",
            restoreRedactedValue("mockserver.llmBackendsConfig",
                "[{\"name\":\"openai\",\"provider\":\"OPENAI\",\"baseUrl\":\"https://other.example\",\"apiKey\":\"sk-new\"}]",
                held), containsString("sk-new"));
        assertThat("the same destination keeps the held key",
            restoreRedactedValue("mockserver.llmBackendsConfig",
                "[{\"name\":\"openai\",\"provider\":\"OPENAI\",\"baseUrl\":\"https://held.example\",\"model\":\"gpt-4o\",\"apiKey\":\"" + REDACTED_VALUE + "\"}]",
                held), containsString("sk-real"));
    }

    @Test
    public void refusesAWholeValueCredentialCarryingTheMaskAndStillAppliesRealOnes() {
        // a credential-NAMED property is masked WHOLE by redactSensitiveValue, so there is no unmasked
        // part left to merge an incoming value against: carrying the mask anywhere makes it unusable,
        // and only a value carrying no mask at all is a real credential
        assertThat("the untouched round trip leaves the held credential in place",
            restoreRedactedValue("mockserver.llmApiKey", REDACTED_VALUE, "sk-real"), is((String) null));
        assertThat("text typed around the mask does not make it a new credential",
            restoreRedactedValue("mockserver.llmApiKey", "sk-" + REDACTED_VALUE, "sk-real"), is((String) null));
        assertThat("a scheme prefixed onto the mask does not make it a new credential",
            restoreRedactedValue("mockserver.prometheusRemoteWriteBearerToken",
                "Bearer " + REDACTED_VALUE, "real-token"), is((String) null));
        assertThat("a real new credential is applied",
            restoreRedactedValue("mockserver.prometheusRemoteWriteBearerToken", "new-token", "old-token"),
            is("new-token"));
        assertThat("an empty value carries no mask, so it still CLEARS the credential",
            restoreRedactedValue("mockserver.prometheusRemoteWriteBasicAuthPassword", "", "old-password"),
            is(""));
    }

    /**
     * The refusal WARN is the only thing an operator gets back from a {@code PUT} that answered
     * {@code 200 OK} having written nothing, and it is visible in neither the return value (every
     * refusal returns {@code null}), so it is asserted here over the log itself: {@code mockserver-core}
     * binds {@code slf4j-jdk14}, so a {@code java.util.logging} handler on the emitting logger sees
     * exactly what is emitted.
     *
     * <p>The property here is credential-SHAPED but NOT one of the three
     * {@code GET /mockserver/configuration} returns as the bare mask, so BOTH a value edited around the
     * mask AND the bare mask itself must warn: neither can be a blessed round trip of that endpoint,
     * because it never emits the mask for this property. The silent case — a bare mask on a property
     * that IS masked on read — is asserted separately in
     * {@link #staysSilentOnAnUntouchedRoundTripOfAPropertyThatIsMaskedOnRead()}.
     *
     * <p>The EDITED case is asserted FIRST on purpose: it proves the handler is capturing, so the
     * bare-mask assertion after it cannot pass because capture silently broke.
     */
    @Test
    public void warnsForBothAnEditedMaskAndABareMaskOnAPropertyNotMaskedOnRead() {
        // a property name used by NO other test, so a class running concurrently in the parallel
        // Surefire phase cannot contribute a record this test counts — several provoke this very
        // warning for llmApiKey and prometheusRemoteWriteHeaders. Credential-shaped (…apikey), in
        // neither structured-value set, and NOT in WHOLE_VALUE_MASKED_CREDENTIAL_PROPERTIES, so a bare
        // mask for it is operator error rather than a round trip and must warn.
        String property = "mockserver.logAssertionOnlyApiKey";

        java.util.logging.Logger emittingLogger =
            java.util.logging.Logger.getLogger("org.mockserver.configuration.EmbeddedCredentialRedaction");
        List<String> captured = new ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()
                    && String.valueOf(record.getMessage()).contains(property)) {
                    synchronized (captured) {
                        captured.add(record.getMessage());
                    }
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Level originalLevel = emittingLogger.getLevel();
        emittingLogger.addHandler(handler);
        emittingLogger.setLevel(java.util.logging.Level.ALL);
        try {
            assertThat(restoreRedactedValue(property, "sk-" + REDACTED_VALUE, "sk-real"), is((String) null));

            assertThat("a value the operator EDITED around the mask writes nothing, so the PUT that "
                    + "answered 200 OK must say so — exactly once", captured.size(), is(1));
            assertThat("the warning must name the property that has to be fixed",
                captured.get(0), containsString(property));
            assertThat("the warning must carry the recovery step, or it says what went wrong and not "
                    + "what to do about it", captured.get(0), containsString("in place of the entire mask"));
            assertThat("the warning must never disclose the credential it is protecting",
                captured.get(0), not(containsString("sk-real")));

            captured.clear();

            // The BARE mask on this property must ALSO warn. It is credential-SHAPED, but it is not a
            // property GET /mockserver/configuration returns masked, so no round trip of that endpoint
            // can have produced this value — it was copied out of GET /mockserver/config,
            // --print-config or the Server Info tab, none of which is a PUT body, or typed by hand.
            // Silence here is what let a pasted mask destroy a working credential undiagnosably: the
            // credential is refused (correctly) and the PUT answers 200 OK with nothing to show for it.
            assertThat(restoreRedactedValue(property, REDACTED_VALUE, "sk-real"), is((String) null));

            assertThat("the bare mask on a property that is never masked by GET /mockserver/configuration "
                    + "is always operator error, and refusing it silently is indistinguishable from "
                    + "applying it", captured.size(), is(1));
            assertThat(captured.get(0), containsString(property));
            assertThat("the warning must never disclose the credential it is protecting",
                captured.get(0), not(containsString("sk-real")));

            captured.clear();
        } finally {
            emittingLogger.removeHandler(handler);
            emittingLogger.setLevel(originalLevel);
        }
    }

    /**
     * The other half of the rule: for the three properties {@code GET /mockserver/configuration} really
     * does return as the bare mask, sending that mask straight back IS the blessed round trip and must
     * stay silent — a config-as-code tool that GETs, edits one unrelated property and PUTs the whole
     * blob would otherwise log a security-flavoured warning per credential on every apply, and anyone
     * alerting on WARN would read it as a credential having been lost.
     *
     * <p>Asserted on a REAL whole-value credential rather than the fictional name above, because the
     * silence is a property of set membership and a fictional name cannot be in the set. Parallel-safe
     * for a narrower reason than its sibling, which must be preserved by any later edit: the filter
     * matches the {@code mockserver.}-PREFIXED name, and every other caller in the repository passes
     * these properties unprefixed (the DTO passes {@code "llmApiKey"}), so no concurrently-running
     * class can contribute a record this test counts. Surefire runs {@code parallel=classes}, so the
     * prefixed-name assertions inside THIS class are serialized with each other.
     */
    @Test
    public void staysSilentOnAnUntouchedRoundTripOfAPropertyThatIsMaskedOnRead() {
        String property = "mockserver.llmApiKey";

        java.util.logging.Logger emittingLogger =
            java.util.logging.Logger.getLogger("org.mockserver.configuration.EmbeddedCredentialRedaction");
        List<String> captured = new ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()
                    && String.valueOf(record.getMessage()).contains(property)) {
                    synchronized (captured) {
                        captured.add(record.getMessage());
                    }
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Level originalLevel = emittingLogger.getLevel();
        emittingLogger.addHandler(handler);
        emittingLogger.setLevel(java.util.logging.Level.ALL);
        try {
            // asserted FIRST so the silence assertion after it cannot pass because capture broke
            assertThat(restoreRedactedValue(property, "sk-" + REDACTED_VALUE, "sk-real"), is((String) null));
            assertThat("an EDITED value must warn even for a property that is masked on read",
                captured.size(), is(1));

            captured.clear();

            assertThat(restoreRedactedValue(property, REDACTED_VALUE, "sk-real"), is((String) null));

            assertThat("the untouched round trip of a property GET /mockserver/configuration returns "
                    + "masked is the blessed flow, not an operator error", captured, is(empty()));
        } finally {
            emittingLogger.removeHandler(handler);
            emittingLogger.setLevel(originalLevel);
        }
    }

    @Test
    public void neverResolvesToAMaskCarryingValueForAPropertyWithNoStructuredShape() {
        // the backstop in restoreRedactedValue itself, and the ONLY assertion that exercises it: no
        // restore* method examines a property outside the two structured shapes, so without this check
        // such a value would be written with the mask intact
        assertThat(restoreRedactedValue("mockserver.someFutureProperty", "prefix-" + REDACTED_VALUE, "real"),
            is((String) null));
        assertThat("a value carrying no mask is still passed straight through",
            restoreRedactedValue("mockserver.someFutureProperty", "plain", "real"), is("plain"));
    }
}
