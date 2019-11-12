package org.mockserver.logging;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.HttpStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.logging.LogManager;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.ConfigurationProperties.javaLoggerLogLevel;
import static org.mockserver.configuration.ConfigurationProperties.logLevel;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.ERROR;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("CharsetObjectCanBeUsed")
public class MockServerLogger {

    static {
        configureLogger();
    }

    public static void configureLogger() {
        try {
            if (isNotBlank(javaLoggerLogLevel()) && System.getProperty("java.util.logging.config.file") == null && System.getProperty("java.util.logging.config.class") == null) {
                String loggingConfiguration = "" +
                    "handlers=org.mockserver.logging.StandardOutConsoleHandler\n" +
                    "org.mockserver.logging.StandardOutConsoleHandler.level=ALL\n" +
                    "org.mockserver.logging.StandardOutConsoleHandler.formatter=java.util.logging.SimpleFormatter\n" +
                    "java.util.logging.SimpleFormatter.format=%1$tF %1$tT  %3$s  %4$s  %5$s %6$s%n\n" +
                    ".level=" + javaLoggerLogLevel() + "\n" +
                    "io.netty.handler.ssl.SslHandler.level=WARNING";
                LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(loggingConfiguration.getBytes(Charset.forName("UTF-8"))));
            }
        } catch (Throwable throwable) {
            new MockServerLogger().logEvent(
                new LogEntry()
                    .setType(SERVER_CONFIGURATION)
                    .setLogLevel(ERROR)
                    .setMessageFormat("Exception while configuring Java logging - " + throwable.getMessage())
                    .setThrowable(throwable)
            );
        }
    }

    private final Logger logger;
    private HttpStateHandler httpStateHandler;

    @VisibleForTesting
    public MockServerLogger() {
        this(MockServerLogger.class);
    }

    @VisibleForTesting
    public MockServerLogger(final Logger logger) {
        this.logger = logger;
        this.httpStateHandler = null;
    }

    public MockServerLogger(final Class loggerClass) {
        this.logger = LoggerFactory.getLogger(loggerClass);
        this.httpStateHandler = null;
    }

    public MockServerLogger(final @Nullable HttpStateHandler httpStateHandler) {
        this.logger = null;
        this.httpStateHandler = httpStateHandler;
    }

    public MockServerLogger setHttpStateHandler(HttpStateHandler httpStateHandler) {
        this.httpStateHandler = httpStateHandler;
        return this;
    }

    public void logEvent(LogEntry logEntry) {
        if (logEntry.getType() == RECEIVED_REQUEST || isEnabled(logEntry.getLogLevel())) {
            if (httpStateHandler != null) {
                httpStateHandler.log(logEntry);
            } else {
                writeToSystemOut(logger, logEntry);
            }
        }
    }

    public static void writeToSystemOut(Logger logger, LogEntry logEntry) {
        if (!ConfigurationProperties.disableSystemOut() &&
            isEnabled(logEntry.getLogLevel()) &&
            isNotBlank(logEntry.getMessage())) {
            switch (logEntry.getLogLevel()) {
                case ERROR:
                    logger.error(logEntry.getMessage(), logEntry.getThrowable());
                    break;
                case WARN:
                    logger.warn(logEntry.getMessage(), logEntry.getThrowable());
                    break;
                case INFO:
                    logger.info(logEntry.getMessage(), logEntry.getThrowable());
                    break;
                case DEBUG:
                    logger.debug(logEntry.getMessage(), logEntry.getThrowable());
                    break;
                case TRACE:
                    logger.trace(logEntry.getMessage(), logEntry.getThrowable());
                    break;
            }
        }
    }

    public static boolean isEnabled(final Level level) {
        return logLevel() != null && level.toInt() >= logLevel().toInt();
    }
}
