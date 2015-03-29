package org.mockserver.configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class ConfigurationPropertiesTest {

    String propertiesBeforeTest;

    @Before
    public void backupProperties() throws IOException {
        StringWriter stringWriter = new StringWriter();
        System.getProperties().store(stringWriter, "");
        propertiesBeforeTest = stringWriter.toString();
    }

    @After
    public void restoreProperties() throws IOException {
        java.util.Properties properties = new java.util.Properties();
        properties.load(new StringReader(propertiesBeforeTest));
        System.setProperties(properties);
    }

    @Test
    public void shouldSetAndReadMaxTimeout() {
        // given
        System.clearProperty("mockserver.maxTimeout");

        // when
        assertEquals(TimeUnit.SECONDS.toMillis(ConfigurationProperties.DEFAULT_MAX_TIMEOUT), new ConfigurationProperties().maxTimeout());
        ConfigurationProperties.maxTimeout(100);

        // then
        assertEquals(100, ConfigurationProperties.maxTimeout());
    }

    @Test
    public void shouldThrowRuntimeExceptionForInvalidMaxTimeout() {
        // given
        System.setProperty("mockserver.maxTimeout", "invalid");

        // then
        assertEquals(TimeUnit.SECONDS.toMillis(ConfigurationProperties.DEFAULT_MAX_TIMEOUT), ConfigurationProperties.maxTimeout());
    }

    @Test
    public void shouldSetAndReadBufferSize() {
        // given
        System.clearProperty("mockserver.requestBufferSize");

        // when
        assertEquals(ConfigurationProperties.DEFAULT_BUFFER_SIZE, ConfigurationProperties.bufferSize());
        ConfigurationProperties.bufferSize(100);

        // then
        assertEquals(100, ConfigurationProperties.bufferSize());
    }

    @Test
    public void shouldThrowRuntimeExceptionForInvalidBufferSize() {
        // given
        System.setProperty("mockserver.requestBufferSize", "invalid");

        // then
        assertEquals(ConfigurationProperties.DEFAULT_BUFFER_SIZE, ConfigurationProperties.bufferSize());
    }

    @Test
    public void shouldSetAndReadServerStopPort() {
        // given
        System.clearProperty("mockserver.mockServerHttpPort");

        // when
        assertEquals(-1, ConfigurationProperties.mockServerPort());
        ConfigurationProperties.mockServerPort(10);

        // then
        assertEquals(10, ConfigurationProperties.mockServerPort());
    }

    @Test
    public void shouldThrowRuntimeExceptionForInvalidServerStopPort() {
        // given
        System.setProperty("mockserver.mockServerHttpPort", "invalid");

        // then
        assertEquals(-1, ConfigurationProperties.mockServerPort());
    }

    @Test
    public void shouldSetAndReadProxyStopPort() {
        // given
        System.clearProperty("mockserver.proxyHttpPort");

        // when
        assertEquals(-1, ConfigurationProperties.proxyPort());
        ConfigurationProperties.proxyPort(10);

        // then
        assertEquals(10, ConfigurationProperties.proxyPort());
    }

    @Test
    public void shouldThrowRuntimeExceptionForInvalidProxyStopPort() {
        // given
        System.setProperty("mockserver.proxyHttpPort", "invalid");

        // then
        assertEquals(-1, ConfigurationProperties.proxyPort());
    }
}
