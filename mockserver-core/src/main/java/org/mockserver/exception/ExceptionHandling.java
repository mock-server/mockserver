package org.mockserver.exception;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import static org.slf4j.event.Level.WARN;

public class ExceptionHandling {

    static MockServerLogger mockServerLogger = new MockServerLogger();

    public static void swallowThrowable(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(WARN)
                    .setMessageFormat(throwable.getMessage())
                    .setThrowable(throwable)
            );
        }
    }

}
