package org.mockserver.configuration;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.configuration.ConfigurationProperties.REDACTED_VALUE;
import static org.mockserver.configuration.ConfigurationProperties.SOURCE_DEFAULT;
import static org.mockserver.configuration.ConfigurationProperties.SOURCE_SYSTEM_PROPERTY;

/**
 * End-to-end verification of the effective-configuration diagnostic against REAL system
 * properties: a non-sensitive key set via a JVM system property reports its value and the
 * {@code system-property} source, a sensitive key is redacted, and an unset key reports the
 * built-in default.
 *
 * <p>This test MUTATES global state (sets JVM system properties), so it is registered in the
 * sequential Surefire phase and excluded from the parallel phase in mockserver-core/pom.xml,
 * and restores every property it touches in a {@code finally} block.</p>
 */
public class ConfigurationPropertiesEffectiveConfigGlobalStateTest {

    private static final String NON_SENSITIVE_KEY = "mockserver.maxExpectations";
    private static final String SENSITIVE_KEY = "mockserver.llmApiKey";
    // A recognised key we deliberately leave unset so it reports its built-in default.
    private static final String DEFAULT_KEY = "mockserver.metricsEnabled";
    // A key whose VALUE embeds a credential rather than being one: whole-property-name redaction does
    // not reach it, so it needs the per-header rule this diagnostic shares with the configuration DTO.
    private static final String EMBEDDED_CREDENTIAL_KEY = "mockserver.prometheusRemoteWriteHeaders";

    private static Optional<ConfigurationProperties.ResolvedProperty> find(List<ConfigurationProperties.ResolvedProperty> properties, String name) {
        return properties.stream().filter(property -> name.equals(property.getName())).findFirst();
    }

    @Test
    public void reportsValueAndSourcePerKeyAndRedactsSecrets() throws Exception {
        String previousNonSensitive = System.getProperty(NON_SENSITIVE_KEY);
        String previousSensitive = System.getProperty(SENSITIVE_KEY);
        String previousDefault = System.getProperty(DEFAULT_KEY);
        // effectiveConfiguration is cache-first, so a leaked cached value would win over the
        // system property we set here — clear the cache entries so the tiers are authoritative.
        String previousCachedNonSensitive = getCacheEntry(NON_SENSITIVE_KEY);
        String previousCachedSensitive = getCacheEntry(SENSITIVE_KEY);
        String previousCachedDefault = getCacheEntry(DEFAULT_KEY);
        try {
            clearCacheEntry(NON_SENSITIVE_KEY);
            clearCacheEntry(SENSITIVE_KEY);
            clearCacheEntry(DEFAULT_KEY);
            System.setProperty(NON_SENSITIVE_KEY, "4242");
            System.setProperty(SENSITIVE_KEY, "super-secret-token-value");
            // Ensure the default key really is unset for this assertion.
            System.clearProperty(DEFAULT_KEY);

            List<ConfigurationProperties.ResolvedProperty> properties = ConfigurationProperties.effectiveConfiguration();

            // Non-sensitive key set via system property: exact value and system-property source.
            ConfigurationProperties.ResolvedProperty nonSensitive = find(properties, NON_SENSITIVE_KEY)
                .orElseThrow(() -> new AssertionError("expected " + NON_SENSITIVE_KEY + " in effective configuration"));
            assertThat(nonSensitive.getValue(), is("4242"));
            assertThat(nonSensitive.getSource(), is(SOURCE_SYSTEM_PROPERTY));

            // Sensitive key set via system property: value redacted, source still reported.
            ConfigurationProperties.ResolvedProperty sensitive = find(properties, SENSITIVE_KEY)
                .orElseThrow(() -> new AssertionError("expected " + SENSITIVE_KEY + " in effective configuration"));
            assertThat(sensitive.getValue(), is(REDACTED_VALUE));
            assertThat(sensitive.getValue(), not(containsString("super-secret-token-value")));
            assertThat(sensitive.getSource(), is(SOURCE_SYSTEM_PROPERTY));

            // Unset key reports the built-in default.
            ConfigurationProperties.ResolvedProperty unset = find(properties, DEFAULT_KEY)
                .orElseThrow(() -> new AssertionError("expected " + DEFAULT_KEY + " in effective configuration"));
            assertThat(unset.getValue(), is("(default)"));
            assertThat(unset.getSource(), is(SOURCE_DEFAULT));
        } finally {
            restoreCacheEntry(NON_SENSITIVE_KEY, previousCachedNonSensitive);
            restoreCacheEntry(SENSITIVE_KEY, previousCachedSensitive);
            restoreCacheEntry(DEFAULT_KEY, previousCachedDefault);
            restore(NON_SENSITIVE_KEY, previousNonSensitive);
            restore(SENSITIVE_KEY, previousSensitive);
            restore(DEFAULT_KEY, previousDefault);
        }
    }

    @Test
    public void textOutputRedactsSecretsAndShowsSources() throws Exception {
        String previousNonSensitive = System.getProperty(NON_SENSITIVE_KEY);
        String previousSensitive = System.getProperty(SENSITIVE_KEY);
        String previousCachedNonSensitive = getCacheEntry(NON_SENSITIVE_KEY);
        String previousCachedSensitive = getCacheEntry(SENSITIVE_KEY);
        try {
            clearCacheEntry(NON_SENSITIVE_KEY);
            clearCacheEntry(SENSITIVE_KEY);
            System.setProperty(NON_SENSITIVE_KEY, "777");
            System.setProperty(SENSITIVE_KEY, "do-not-print-me");

            String text = ConfigurationProperties.effectiveConfigurationAsText();

            assertThat(text, containsString(NON_SENSITIVE_KEY + " = 777   [" + SOURCE_SYSTEM_PROPERTY + "]"));
            assertThat(text, containsString(SENSITIVE_KEY + " = " + REDACTED_VALUE + "   [" + SOURCE_SYSTEM_PROPERTY + "]"));
            assertThat(text, not(containsString("do-not-print-me")));
        } finally {
            restoreCacheEntry(NON_SENSITIVE_KEY, previousCachedNonSensitive);
            restoreCacheEntry(SENSITIVE_KEY, previousCachedSensitive);
            restore(NON_SENSITIVE_KEY, previousNonSensitive);
            restore(SENSITIVE_KEY, previousSensitive);
        }
    }

    @Test
    public void jsonOutputRedactsSecretsAndShowsSources() throws Exception {
        String previousSensitive = System.getProperty(SENSITIVE_KEY);
        String previousCachedSensitive = getCacheEntry(SENSITIVE_KEY);
        try {
            clearCacheEntry(SENSITIVE_KEY);
            System.setProperty(SENSITIVE_KEY, "do-not-print-me-json");

            String json = ConfigurationProperties.effectiveConfigurationAsJson();

            assertThat(json, containsString("\"name\":\"" + SENSITIVE_KEY + "\""));
            assertThat(json, containsString("\"value\":\"" + REDACTED_VALUE + "\""));
            assertThat(json, containsString("\"source\":\"" + SOURCE_SYSTEM_PROPERTY + "\""));
            assertThat(json, not(containsString("do-not-print-me-json")));
        } finally {
            restoreCacheEntry(SENSITIVE_KEY, previousCachedSensitive);
            restore(SENSITIVE_KEY, previousSensitive);
        }
    }

    @Test
    public void viewOfOnlyNonDefaultsMasksEveryCredentialAndShowsNoValueOfAnUnrecognisedKey() throws Exception {
        java.util.Map<String, String> previous = new java.util.HashMap<>();
        java.util.List<String> credentialKeys = new java.util.ArrayList<>();
        for (String key : ConfigurationProperties.recognisedSystemPropertyKeys()) {
            if (ConfigurationProperties.isSensitivePropertyName(key)) {
                credentialKeys.add(key);
            }
        }
        String typoKey = "mockserver.maxExpectatons";
        java.util.List<String> touched = new java.util.ArrayList<>(credentialKeys);
        touched.add(typoKey);
        touched.add(EMBEDDED_CREDENTIAL_KEY);
        touched.add(DEFAULT_KEY);
        for (String key : touched) {
            previous.put(key, System.getProperty(key));
        }
        // an earlier test may have set DEFAULT_KEY with a setter, which the view rightly reports as runtime-set
        String previousCachedDefault = getCacheEntry(DEFAULT_KEY);
        boolean defaultWasProgrammaticallySet = isProgrammaticallySet(DEFAULT_KEY);
        try {
            clearCacheEntry(DEFAULT_KEY);
            removeProgrammaticallySet(DEFAULT_KEY);
            for (int i = 0; i < credentialKeys.size(); i++) {
                System.setProperty(credentialKeys.get(i), "credential-value-" + i + "-must-not-appear");
            }
            System.setProperty(typoKey, "typo-value-must-not-appear");
            System.setProperty(EMBEDDED_CREDENTIAL_KEY, "X-Api-Key=embedded-value-must-not-appear");
            System.clearProperty(DEFAULT_KEY);

            String json = ConfigurationProperties.effectiveConfigurationAsJson(true);
            String text = ConfigurationProperties.effectiveConfigurationAsText();

            assertThat(credentialKeys.size(), org.hamcrest.Matchers.greaterThan(20));
            for (String output : java.util.List.of(json, text)) {
                assertThat(output, not(containsString("must-not-appear")));
                assertThat(output, containsString(typoKey));
                assertThat(output, containsString("mockserver.llmApiKey"));
            }
            for (String key : credentialKeys) {
                assertThat(key, json, containsString("{\"name\":\"" + key + "\",\"value\":\"" + REDACTED_VALUE + "\""));
            }
            assertThat(json, containsString("\"name\":\"" + typoKey + "\",\"value\":\"(ignored)\",\"source\":\"unrecognised\",\"setAs\":\"system property\",\"suggestions\":[\"mockserver.maxExpectations\"]"));
            assertThat(json, not(containsString("\"source\":\"" + SOURCE_DEFAULT + "\"")));
            assertThat(json, not(containsString("\"name\":\"" + DEFAULT_KEY + "\"")));
            assertThat(ConfigurationProperties.effectiveConfigurationAsJson(false), containsString("\"name\":\"" + DEFAULT_KEY + "\""));
            assertThat(text, containsString(typoKey + "   [system property]   did you mean mockserver.maxExpectations?"));
        } finally {
            for (String key : touched) {
                restore(key, previous.get(key));
            }
            restoreCacheEntry(DEFAULT_KEY, previousCachedDefault);
            restoreProgrammaticallySet(DEFAULT_KEY, defaultWasProgrammaticallySet);
        }
    }

    @Test
    public void aPropertyIsExplicitlyConfiguredOnlyWhileATierSuppliesIt() throws Exception {
        String key = "mockserver.localBoundIP";
        String previous = System.getProperty(key);
        boolean wasProgrammaticallySet = isProgrammaticallySet(key);
        try {
            System.clearProperty(key);
            removeProgrammaticallySet(key);
            assertThat("MOCKSERVER_LOCAL_BOUND_IP must not be set for this test", System.getenv("MOCKSERVER_LOCAL_BOUND_IP"), org.hamcrest.Matchers.nullValue());
            assertThat(ConfigurationProperties.isExplicitlyConfigured("localBoundIP"), is(false));
            System.setProperty(key, "0.0.0.0");
            assertThat(ConfigurationProperties.isExplicitlyConfigured("localBoundIP"), is(true));
            assertThat(ConfigurationProperties.isExplicitlyConfigured("notAProperty"), is(false));
        } finally {
            restoreProgrammaticallySet(key, wasProgrammaticallySet);
            restore(key, previous);
        }
    }

    @Test
    public void redactsACredentialEmbeddedInsideAStructuredValueOnEverySurface() throws Exception {
        // GET /mockserver/config is the SIBLING of GET /mockserver/configuration on the same, by
        // default unauthenticated, control plane. Redacting by whole property name only leaves this
        // endpoint disclosing a credential the configuration endpoint masks — one leak closed, its
        // twin left open. Both must apply the same rule.
        String previous = System.getProperty(EMBEDDED_CREDENTIAL_KEY);
        String previousCached = getCacheEntry(EMBEDDED_CREDENTIAL_KEY);
        try {
            clearCacheEntry(EMBEDDED_CREDENTIAL_KEY);
            System.setProperty(EMBEDDED_CREDENTIAL_KEY, "Authorization=Bearer REAL-TOKEN-XYZ,X-Scope-OrgID=tenant-a");

            String expected = "Authorization=" + REDACTED_VALUE + ",X-Scope-OrgID=tenant-a";

            ConfigurationProperties.ResolvedProperty resolved = find(ConfigurationProperties.effectiveConfiguration(), EMBEDDED_CREDENTIAL_KEY)
                .orElseThrow(() -> new AssertionError("expected " + EMBEDDED_CREDENTIAL_KEY + " in effective configuration"));
            assertThat(resolved.getValue(), is(expected));
            assertThat(resolved.getSource(), is(SOURCE_SYSTEM_PROPERTY));

            String json = ConfigurationProperties.effectiveConfigurationAsJson();
            assertThat("GET /mockserver/config must not disclose a credential embedded in a value",
                json, not(containsString("REAL-TOKEN-XYZ")));
            assertThat(json, containsString("\"value\":\"" + expected + "\""));

            assertThat("--print-config must not disclose it either",
                ConfigurationProperties.effectiveConfigurationAsText(), not(containsString("REAL-TOKEN-XYZ")));
        } finally {
            restoreCacheEntry(EMBEDDED_CREDENTIAL_KEY, previousCached);
            restore(EMBEDDED_CREDENTIAL_KEY, previous);
        }
    }

    @Test
    public void reportsCachedRuntimeValueSetViaProgrammaticSetter() throws Exception {
        // readPropertyHierarchically is cache-first, so a programmatic setter is what the server
        // actually uses; the diagnostic must reflect the cached value, not a stale tier value.
        String previousNonSensitive = System.getProperty(NON_SENSITIVE_KEY);
        try {
            ConfigurationProperties.maxExpectations(31337);

            ConfigurationProperties.ResolvedProperty resolved = find(ConfigurationProperties.effectiveConfiguration(), NON_SENSITIVE_KEY)
                .orElseThrow(() -> new AssertionError("expected " + NON_SENSITIVE_KEY + " in effective configuration"));
            assertThat(resolved.getValue(), is("31337"));
            // The setter also sets the JVM system property, so the source is attributed to that tier.
            assertThat(resolved.getSource(), is(SOURCE_SYSTEM_PROPERTY));
        } finally {
            // Clear BOTH the cache entry and the system property the setter wrote.
            clearCacheEntry(NON_SENSITIVE_KEY);
            restore(NON_SENSITIVE_KEY, previousNonSensitive);
        }
    }

    @Test
    public void unsetButAlreadyReadKeyReportsDefaultNotRuntimeSet() throws Exception {
        // readPropertyHierarchically caches EVERY value it resolves, including the built-in default
        // of a property left unset in all tiers. The diagnostic must not mistake that memoised
        // default for a programmatic runtime override.
        String previousDefault = System.getProperty(DEFAULT_KEY);
        String previousCachedDefault = getCacheEntry(DEFAULT_KEY);
        boolean wasProgrammaticallySet = isProgrammaticallySet(DEFAULT_KEY);
        try {
            // Ensure no tier supplies a value and no stale cache/registry entry survives.
            System.clearProperty(DEFAULT_KEY);
            clearCacheEntry(DEFAULT_KEY);
            removeProgrammaticallySet(DEFAULT_KEY);

            // Read the accessor: this populates the cache with the built-in default (false) WITHOUT
            // going through a setter, so the key is cached but not in the programmatic-set registry.
            ConfigurationProperties.metricsEnabled();
            assertThat("precondition: accessor should cache the default", getCacheEntry(DEFAULT_KEY), is("false"));

            ConfigurationProperties.ResolvedProperty resolved = find(ConfigurationProperties.effectiveConfiguration(), DEFAULT_KEY)
                .orElseThrow(() -> new AssertionError("expected " + DEFAULT_KEY + " in effective configuration"));
            // The memoised default must be reported as the default, NOT runtime-set with the value false.
            assertThat(resolved.getValue(), is("(default)"));
            assertThat(resolved.getSource(), is(SOURCE_DEFAULT));
        } finally {
            clearCacheEntry(DEFAULT_KEY);
            restoreProgrammaticallySet(DEFAULT_KEY, wasProgrammaticallySet);
            restoreCacheEntry(DEFAULT_KEY, previousCachedDefault);
            restore(DEFAULT_KEY, previousDefault);
        }
    }

    @Test
    public void programmaticSetterThatOverridesDefaultIsReportedNotAsDefault() throws Exception {
        // A genuine programmatic override (metricsEnabled(true)) must report the override value and a
        // concrete source — never (default) — even though the property has no static tier value.
        String previousDefault = System.getProperty(DEFAULT_KEY);
        String previousCachedDefault = getCacheEntry(DEFAULT_KEY);
        boolean wasProgrammaticallySet = isProgrammaticallySet(DEFAULT_KEY);
        try {
            System.clearProperty(DEFAULT_KEY);
            clearCacheEntry(DEFAULT_KEY);
            removeProgrammaticallySet(DEFAULT_KEY);

            ConfigurationProperties.metricsEnabled(true);

            ConfigurationProperties.ResolvedProperty resolved = find(ConfigurationProperties.effectiveConfiguration(), DEFAULT_KEY)
                .orElseThrow(() -> new AssertionError("expected " + DEFAULT_KEY + " in effective configuration"));
            assertThat(resolved.getValue(), is("true"));
            assertThat(resolved.getValue(), not("(default)"));
            // The setter also writes the JVM system property, so the source is attributed to that tier.
            assertThat(resolved.getSource(), is(SOURCE_SYSTEM_PROPERTY));
        } finally {
            clearCacheEntry(DEFAULT_KEY);
            removeProgrammaticallySet(DEFAULT_KEY);
            restoreProgrammaticallySet(DEFAULT_KEY, wasProgrammaticallySet);
            restoreCacheEntry(DEFAULT_KEY, previousCachedDefault);
            restore(DEFAULT_KEY, previousDefault);
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<String> programmaticallySetKeys() throws Exception {
        java.lang.reflect.Field field = ConfigurationProperties.class.getDeclaredField("programmaticallySetKeys");
        field.setAccessible(true);
        Object set = field.get(null);
        return set instanceof java.util.Set ? (java.util.Set<String>) set : null;
    }

    private static boolean isProgrammaticallySet(String key) throws Exception {
        java.util.Set<String> set = programmaticallySetKeys();
        return set != null && set.contains(key);
    }

    private static void removeProgrammaticallySet(String key) throws Exception {
        java.util.Set<String> set = programmaticallySetKeys();
        if (set != null) {
            set.remove(key);
        }
    }

    private static void restoreProgrammaticallySet(String key, boolean wasSet) throws Exception {
        java.util.Set<String> set = programmaticallySetKeys();
        if (set != null) {
            if (wasSet) {
                set.add(key);
            } else {
                set.remove(key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, String> propertyCache() throws Exception {
        java.lang.reflect.Field cacheField = ConfigurationProperties.class.getDeclaredField("propertyCache");
        cacheField.setAccessible(true);
        Object cache = cacheField.get(null);
        return cache instanceof java.util.Map ? (java.util.Map<String, String>) cache : null;
    }

    private static String getCacheEntry(String key) throws Exception {
        java.util.Map<String, String> cache = propertyCache();
        return cache != null ? cache.get(key) : null;
    }

    private static void clearCacheEntry(String key) throws Exception {
        java.util.Map<String, String> cache = propertyCache();
        if (cache != null) {
            cache.remove(key);
        }
    }

    private static void restoreCacheEntry(String key, String previousValue) throws Exception {
        java.util.Map<String, String> cache = propertyCache();
        if (cache != null) {
            if (previousValue != null) {
                cache.put(key, previousValue);
            } else {
                cache.remove(key);
            }
        }
    }

    private static void restore(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
