package org.mockserver.logging;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.util.List;

import static org.mockserver.configuration.ConfigurationProperties.logLevel;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class MockServerLogger {

    public static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger();
    private final Logger logger;
    private final HttpStateHandler httpStateHandler;

    public MockServerLogger() {
        this(MockServerLogger.class);
    }

    public MockServerLogger(Class loggerClass) {
        this(LoggerFactory.getLogger(loggerClass), null);
    }

    public MockServerLogger(Logger logger, @Nullable HttpStateHandler httpStateHandler) {
        this.logger = logger;
        this.httpStateHandler = httpStateHandler;
    }

    public void trace(String message, Object... arguments) {
        trace(null, message, arguments);
    }

    public void trace(HttpRequest request, String message, Object... arguments) {
        if (isEnabled(TRACE)) {
            String logMessage = formatLogMessage(message, arguments);
            logger.trace(logMessage);
            logger.info(logMessage);
            addLogEvents(request, logMessage);
        }
    }

    public void debug(String message, Object... arguments) {
        debug(null, message, arguments);
    }

    public void debug(HttpRequest request, String message, Object... arguments) {
        if (isEnabled(DEBUG)) {
            String logMessage = formatLogMessage(message, arguments);
            logger.trace(logMessage);
            logger.info(logMessage);
            addLogEvents(request, logMessage);
        }
    }

    public void info(String message, Object... arguments) {
        info((HttpRequest) null, message, arguments);
    }

    public void info(HttpRequest request, String message, Object... arguments) {
        if (isEnabled(INFO)) {
            String logMessage = formatLogMessage(message, arguments);
            logger.info(logMessage);
            addLogEvents(request, logMessage);
        }
    }

    public void info(List<HttpRequest> requests, String message, Object... arguments) {
        if (isEnabled(INFO)) {
            String logMessage = formatLogMessage(message, arguments);
            logger.info(logMessage);
            addLogEvents(requests, logMessage);
        }
    }

    public void warn(String message) {
        warn((HttpRequest) null, message);
    }

    public void warn(String message, Object... arguments) {
        warn(null, message, arguments);
    }

    public void warn(@Nullable HttpRequest request, String message, Object... arguments) {
        if (isEnabled(WARN)) {
            String logMessage = formatLogMessage(message, arguments);
            logger.error(logMessage);
            addLogEvents(request, logMessage);
        }
    }

    public void error(String message, Throwable throwable) {
        error((HttpRequest) null, throwable, message);
    }

    public void error(@Nullable HttpRequest request, String message, Object... arguments) {
        error(request, null, message, arguments);
    }

    public void error(@Nullable HttpRequest request, Throwable throwable, String message, Object... arguments) {
        if (isEnabled(ERROR)) {
            String logMessage = formatLogMessage(message, arguments);
            logger.error(logMessage, throwable);
            addLogEvents(request, logMessage);
        }
    }

    public void error(List<HttpRequest> requests, Throwable throwable, String message, Object... arguments) {
        if (isEnabled(ERROR)) {
            String logMessage = formatLogMessage(message, arguments);
            logger.error(logMessage, throwable);
            addLogEvents(requests, logMessage);
        }
    }

    private void addLogEvents(@Nullable HttpRequest request, String logMessage) {
        if (httpStateHandler != null) {
            httpStateHandler.log(new MessageLogEntry(request, logMessage));
        }
    }

    private void addLogEvents(List<HttpRequest> requests, String logMessage) {
        if (httpStateHandler != null) {
            for (HttpRequest httpRequest : requests) {
                httpStateHandler.log(new MessageLogEntry(httpRequest, logMessage));
            }
        }
    }

    public boolean isEnabled(Level level) {
        return level.toInt() >= ConfigurationProperties.logLevel().toInt();
    }
}
