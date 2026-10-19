package org.mockserver.logging;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.MockServerEventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * {@code disableSystemOut} is what leaves the event log's console output unread, through the logging
 * configuration it installs. Changes the JVM-wide logging configuration, so it runs in the sequential test phase.
 */
public class LogOutputProbeDisableSystemOutTest {

    private boolean originalDisableSystemOut;

    @Before
    public void saveConfiguration() {
        originalDisableSystemOut = ConfigurationProperties.disableSystemOut();
    }

    @After
    public void restoreConfiguration() {
        ConfigurationProperties.disableSystemOut(originalDisableSystemOut);
    }

    @Test
    public void shouldDiscardTheEventLogsConsoleOutputOnlyWhileSystemOutIsDisabled() {
        Logger eventLogLogger = LoggerFactory.getLogger(MockServerEventLog.class);

        ConfigurationProperties.disableSystemOut(true);
        assertThat(LogOutputProbe.discards(eventLogLogger, Level.ERROR), is(true));

        ConfigurationProperties.disableSystemOut(false);
        assertThat(LogOutputProbe.discards(eventLogLogger, Level.ERROR), is(false));
    }
}
