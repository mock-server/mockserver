package org.mockserver.serialization.model;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.configuration.Configuration.configuration;

public class ConfigurationDTOTest {

    @Test
    public void shouldBuildObjectFromDTO() {
        ConfigurationDTO dto = new ConfigurationDTO(configuration()
            .logLevel(Level.DEBUG)
            .maxExpectations(500)
            .metricsEnabled(true)
            .corsAllowOrigin("https://example.com"));

        Configuration config = dto.buildObject();

        assertThat(config.logLevel(), is(Level.DEBUG));
        assertThat(config.maxExpectations(), is(500));
        assertThat(config.metricsEnabled(), is(true));
        assertThat(config.corsAllowOrigin(), is("https://example.com"));
    }

    @Test
    public void shouldApplyOnlyNonNullFieldsToTarget() {
        Configuration target = configuration()
            .logLevel(Level.INFO)
            .maxExpectations(100)
            .metricsEnabled(false)
            .corsAllowOrigin("https://original.com");

        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setLogLevel("WARN");
        dto.setMaxExpectations(999);

        dto.applyTo(target);

        assertThat(target.logLevel(), is(Level.WARN));
        assertThat(target.maxExpectations(), is(999));
        assertThat(target.metricsEnabled(), is(false));
        assertThat(target.corsAllowOrigin(), is("https://original.com"));
    }

    @Test
    public void shouldCreateDTOFromConfiguration() {
        Configuration config = configuration()
            .logLevel(Level.ERROR)
            .maxExpectations(2000)
            .disableLogging(true);

        ConfigurationDTO dto = new ConfigurationDTO(config);

        assertThat(dto.getLogLevel(), is("ERROR"));
        assertThat(dto.getMaxExpectations(), is(2000));
        assertThat(dto.getDisableLogging(), is(true));
    }

    @Test
    public void shouldRoundTripDTOAndConfiguration() {
        Configuration original = configuration()
            .logLevel(Level.TRACE)
            .maxExpectations(42)
            .maxLogEntries(1234)
            .enableCORSForAPI(true);

        ConfigurationDTO dto = new ConfigurationDTO(original);
        Configuration rebuilt = dto.buildObject();

        assertThat(rebuilt.logLevel(), is(original.logLevel()));
        assertThat(rebuilt.maxExpectations(), is(original.maxExpectations()));
        assertThat(rebuilt.maxLogEntries(), is(original.maxLogEntries()));
        assertThat(rebuilt.enableCORSForAPI(), is(original.enableCORSForAPI()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidLogLevel() {
        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setLogLevel("INVALID_LEVEL");
        dto.applyTo(configuration());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativeMaxExpectations() {
        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setMaxExpectations(-1);
        dto.applyTo(configuration());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativeMaxLogEntries() {
        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setMaxLogEntries(-100);
        dto.applyTo(configuration());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativeMaxWebSocketExpectations() {
        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setMaxWebSocketExpectations(-1);
        dto.applyTo(configuration());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectExcessiveMaxExpectations() {
        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setMaxExpectations(200000);
        dto.applyTo(configuration());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectExcessiveMaxLogEntries() {
        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setMaxLogEntries(2000000);
        dto.applyTo(configuration());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectExcessiveMaxWebSocketExpectations() {
        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setMaxWebSocketExpectations(200000);
        dto.applyTo(configuration());
    }

    @Test
    public void shouldNotPartiallyMutateOnValidationFailure() {
        Configuration target = configuration()
            .metricsEnabled(false)
            .maxExpectations(100);

        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setMetricsEnabled(true);
        dto.setMaxExpectations(200000);

        try {
            dto.applyTo(target);
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertThat(target.metricsEnabled(), is(false));
        assertThat(target.maxExpectations(), is(100));
    }

    @Test
    public void shouldNotPartiallyMutateOnProxyParsingFailure() {
        Configuration target = configuration()
            .metricsEnabled(false);

        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setMetricsEnabled(true);
        dto.setForwardHttpProxy("not_a_valid_host_port");

        try {
            dto.applyTo(target);
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertThat(target.metricsEnabled(), is(false));
    }

    @Test
    public void shouldRoundTripLogLevelOverrides() {
        Map<String, String> overrides = ImmutableMap.of("MATCHING", "WARN", "EXPECTATION_MATCHED", "INFO");
        Configuration original = configuration()
            .logLevel(Level.DEBUG)
            .logLevelOverrides(overrides);

        ConfigurationDTO dto = new ConfigurationDTO(original);
        assertThat(dto.getLogLevelOverrides(), equalTo(overrides));

        Configuration rebuilt = dto.buildObject();
        assertThat(rebuilt.logLevelOverrides(), equalTo(overrides));
    }

    @Test
    public void shouldApplyLogLevelOverridesPartially() {
        Configuration target = configuration()
            .logLevel(Level.INFO)
            .logLevelOverrides(Collections.emptyMap());

        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setLogLevelOverrides(ImmutableMap.of("MATCHING", "ERROR"));

        dto.applyTo(target);

        assertThat(target.logLevelOverrides(), equalTo(ImmutableMap.of("MATCHING", "ERROR")));
        assertThat(target.logLevel(), is(Level.INFO));
    }

    @Test
    public void shouldNotApplyLogLevelOverridesWhenNull() {
        Map<String, String> original = ImmutableMap.of("SERVER", "WARN");
        Configuration target = configuration()
            .logLevelOverrides(original);

        ConfigurationDTO dto = new ConfigurationDTO();
        dto.applyTo(target);

        assertThat(target.logLevelOverrides(), equalTo(original));
    }

    @Test
    public void shouldSerializeEmptyLogLevelOverridesAsNull() {
        Configuration config = configuration()
            .logLevelOverrides(Collections.emptyMap());

        ConfigurationDTO dto = new ConfigurationDTO(config);
        assertThat(dto.getLogLevelOverrides(), nullValue());
    }
}
