package org.mockserver.logging;

import org.mockserver.log.model.LogEntry;

import static org.slf4j.event.Level.INFO;

public class BasicLogger {

    public static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(BasicLogger.class);

    public static void logInfo(String message) {
        MOCK_SERVER_LOGGER.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setMessageFormat(message)
        );
    }
}
