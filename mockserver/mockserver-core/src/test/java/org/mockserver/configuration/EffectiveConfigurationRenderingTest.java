package org.mockserver.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.configuration.ConfigurationProperties.*;

/**
 * How the effective-configuration view renders, from explicit inputs: the {@code nonDefault} filter, the unrecognised
 * keys with their suggestions, and that no value of an unrecognised key is ever shown.
 */
public class EffectiveConfigurationRenderingTest {

    private static final String SECRET = "sk-live-do-not-disclose";

    private static final List<ResolvedProperty> PROPERTIES = List.of(
        new ResolvedProperty("mockserver.llmApiKey", REDACTED_VALUE, SOURCE_ENVIRONMENT_VARIABLE),
        new ResolvedProperty("mockserver.maxExpectations", "4242", SOURCE_SYSTEM_PROPERTY),
        new ResolvedProperty("mockserver.metricsEnabled", "false", SOURCE_DEFAULT)
    );

    private static List<UnrecognisedProperty> unrecognisedWithSecretValues() {
        Properties file = new Properties();
        file.setProperty("mockserver.llmApiKeyy", SECRET);
        return findUnrecognisedConfigurationKeys(Set.of("mockserver.typoOne"), Set.of("MOCKSERVER_METRICS_ENABLE"), file);
    }

    private static JsonNode parse(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    private static JsonNode entry(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (name.equals(node.get("name").asText())) {
                return node;
            }
        }
        return null;
    }

    @Test
    public void listsEveryPropertyByDefault() throws Exception {
        JsonNode json = parse(effectiveConfigurationAsJson(PROPERTIES, emptyList(), false));

        assertThat(json.size(), is(3));
        assertThat(entry(json, "mockserver.metricsEnabled").get("source").asText(), is(SOURCE_DEFAULT));
    }

    @Test
    public void nonDefaultLeavesOutThePropertiesAtTheirDefault() throws Exception {
        JsonNode json = parse(effectiveConfigurationAsJson(PROPERTIES, emptyList(), true));

        assertThat(json.size(), is(2));
        assertThat(entry(json, "mockserver.metricsEnabled"), is(nullValue()));
        assertThat(entry(json, "mockserver.maxExpectations").get("value").asText(), is("4242"));
        assertThat(entry(json, "mockserver.llmApiKey").get("value").asText(), is(REDACTED_VALUE));
    }

    @Test
    public void listsUnrecognisedKeysWithTheirSuggestionsEvenWhenOnlyNonDefaultsAreShown() throws Exception {
        JsonNode json = parse(effectiveConfigurationAsJson(PROPERTIES, unrecognisedWithSecretValues(), true));

        JsonNode typo = entry(json, "MOCKSERVER_METRICS_ENABLE");
        assertThat(typo.get("source").asText(), is(SOURCE_UNRECOGNISED));
        assertThat(typo.get("value").asText(), is("(ignored)"));
        assertThat(typo.get("setAs").asText(), is("environment variable"));
        assertThat(typo.get("suggestions").get(0).asText(), is("MOCKSERVER_METRICS_ENABLED"));

        JsonNode noSuggestion = entry(json, "mockserver.typoOne");
        assertThat(noSuggestion.get("suggestions").size(), is(0));
        assertThat(json.size(), is(5));
    }

    @Test
    public void neverShowsTheValueOfAnUnrecognisedKey() {
        List<UnrecognisedProperty> unrecognised = unrecognisedWithSecretValues();

        String json = effectiveConfigurationAsJson(PROPERTIES, unrecognised, false);
        String text = effectiveConfigurationAsText(PROPERTIES, unrecognised);

        assertThat(json, containsString("mockserver.llmApiKeyy"));
        assertThat(json, not(containsString(SECRET)));
        assertThat(text, containsString("mockserver.llmApiKeyy"));
        assertThat(text, not(containsString(SECRET)));
    }

    @Test
    public void textListsUnrecognisedKeysAfterTheProperties() {
        String text = effectiveConfigurationAsText(PROPERTIES, unrecognisedWithSecretValues());

        assertThat(text, containsString("  mockserver.maxExpectations = 4242   [system-property]"));
        assertThat(text, containsString("Unrecognised configuration, ignored (names only):"));
        assertThat(text, containsString("  MOCKSERVER_METRICS_ENABLE   [environment variable]   did you mean MOCKSERVER_METRICS_ENABLED?"));
        assertThat(text, containsString("  mockserver.llmApiKeyy   [properties-file key]   did you mean mockserver.llmApiKey?"));
        assertThat(text, containsString("  mockserver.typoOne   [system property]" + org.mockserver.character.Character.NEW_LINE));
        assertThat(text.indexOf("Unrecognised configuration"), greaterThan(text.indexOf("mockserver.metricsEnabled")));
    }

    @Test
    public void textHasNoUnrecognisedSectionWhenEveryKeyIsRecognised() {
        String text = effectiveConfigurationAsText(PROPERTIES, findUnrecognisedConfigurationKeys(Set.of("mockserver.maxExpectations"), emptySet(), new Properties()));

        assertThat(text, not(containsString("Unrecognised")));
    }

    @Test
    public void saysASystemPropertyOnlyKnobInThePropertiesFileMustBeSetAsASystemProperty() throws Exception {
        Properties file = new Properties();
        file.setProperty("mockserver.jsonSchemaAllowRemoteRefs", "true");
        List<UnrecognisedProperty> unrecognised = findUnrecognisedConfigurationKeys(emptySet(), emptySet(), file);

        JsonNode entry = entry(parse(effectiveConfigurationAsJson(PROPERTIES, unrecognised, true)), "mockserver.jsonSchemaAllowRemoteRefs");
        assertThat(entry.get("source").asText(), is(SOURCE_UNRECOGNISED));
        assertThat(entry.get("systemPropertyOnly").asBoolean(), is(true));
        assertThat(effectiveConfigurationAsText(PROPERTIES, unrecognised),
            containsString("  mockserver.jsonSchemaAllowRemoteRefs   [properties-file key]   read only as a system property: set -Dmockserver.jsonSchemaAllowRemoteRefs instead"));
    }
}
