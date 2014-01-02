package org.mockserver.configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class SystemPropertiesTest {

    String propertiesBeforeTest;

    @Before
    public void backupProperties() throws IOException {
        StringWriter stringWriter = new StringWriter();
        System.getProperties().store(stringWriter, "");
        propertiesBeforeTest = stringWriter.toString();
    }

    @After
    public void restoreProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(propertiesBeforeTest));
        System.setProperties(properties);
    }

    @Test
    public void shouldSetAndReadMaxTimeout() {
        // given
        System.clearProperty("mockserver.maxTimeout");

        // when
        assertEquals(TimeUnit.SECONDS.toMillis(SystemProperties.DEFAULT_MAX_TIMEOUT), SystemProperties.maxTimeout());
        SystemProperties.maxTimeout(100);

        // then
        assertEquals(100, SystemProperties.maxTimeout());
    }

    @Test
    public void shouldSetAndReadBufferSize() {
        // given
        System.clearProperty("mockserver.requestBufferSize");

        // when
        assertEquals(SystemProperties.DEFAULT_BUFFER_SIZE, SystemProperties.bufferSize());
        SystemProperties.bufferSize(100);

        // then
        assertEquals(100, SystemProperties.bufferSize());
    }

    @Test
    public void shouldSetAndReadStopPort() {
        // given
        System.clearProperty("mockserver.stopPort");

        // when
        assertEquals(3, SystemProperties.stopPort(1, 2));
        SystemProperties.stopPort(10);

        // then
        assertEquals(10, SystemProperties.stopPort(1, 2));
    }

    @Test
    public void shouldReturnDefaultStopPort() {
        assertEquals(1, SystemProperties.stopPort(null, null));
        assertEquals(2, SystemProperties.stopPort(1, null));
        assertEquals(3, SystemProperties.stopPort(null, 2));
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidMaxTimeout() {
        // given
        System.setProperty("mockserver.maxTimeout", "invalid");

        // then
        SystemProperties.maxTimeout();
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidBufferSize() {
        // given
        System.setProperty("mockserver.requestBufferSize", "invalid");

        // then
        SystemProperties.bufferSize();
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidStopPort() {
        // given
        System.setProperty("mockserver.stopPort", "invalid");

        // then
        SystemProperties.stopPort(1, 2);
    }
}
