package org.mockserver.logging;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.HttpState;
import org.mockserver.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.logging.LogManager;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.log.model.LogEntry.LogMessageTypeCategory.resolveEffectiveLevel;
import static org.slf4j.event.Level.ERROR;

/**
 * @author jamesdbloom
 */
public class MockServerLogger {

    static {
        configureLogger();
    }

    public static void configureLogger() {
        try {
            if (System.getProperty("java.util.logging.config.file") == null && System.getProperty("java.util.logging.config.class") == null) {
                LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(("" +
                    "handlers=org.mockserver.logging.StandardOutConsoleHandler" + NEW_LINE +
                    "org.mockserver.logging.StandardOutConsoleHandler.level=ALL" + NEW_LINE +
                    "org.mockserver.logging.StandardOutConsoleHandler.formatter=java.util.logging.SimpleFormatter" + NEW_LINE +
                    "java.util.logging.SimpleFormatter.format=%1$tF %1$tT " + Version.getVersion() + " %4$s %5$s %6$s%n" + NEW_LINE +
                    "org.mockserver.level=INFO" + NEW_LINE +
                    "io.netty.level=WARNING").getBytes(UTF_8)));
                if (isNotBlank(ConfigurationProperties.javaLoggerLogLevel())) {
                    String loggingConfiguration = "" +
                        (!ConfigurationProperties.disableSystemOut() ? "handlers=org.mockserver.logging.StandardOutConsoleHandler" + NEW_LINE +
                            "org.mockserver.logging.StandardOutConsoleHandler.level=ALL" + NEW_LINE +
                            "org.mockserver.logging.StandardOutConsoleHandler.formatter=java.util.logging.SimpleFormatter" + NEW_LINE : "") +
                        "java.util.logging.SimpleFormatter.format=%1$tF %1$tT " + Version.getVersion() + " %4$s %5$s %6$s%n" + NEW_LINE +
                        "org.mockserver.level=" + ConfigurationProperties.javaLoggerLogLevel() + NEW_LINE +
                        "io.netty.level=" + (Arrays.asList("TRACE", "FINEST").contains(ConfigurationProperties.javaLoggerLogLevel()) ? "FINE" : "WARNING");
                    LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(loggingConfiguration.getBytes(UTF_8)));
                }
            }
        } catch (Throwable throwable) {
            new MockServerLogger().logEvent(
                new LogEntry()
                    .setType(SERVER_CONFIGURATION)
                    .setLogLevel(ERROR)
                    .setMessageFormat("exception while configuring Java logging - " + throwable.getMessage())
                    .setThrowable(throwable)
            );
        }
    }

    private final Configuration configuration;
    private final Logger logger;
    private HttpState httpStateHandler;

    @VisibleForTesting
    public MockServerLogger() {
        this(MockServerLogger.class);
    }

    @VisibleForTesting
    public MockServerLogger(final Logger logger) {
        this.configuration = null;
        this.logger = logger;
        this.httpStateHandler = null;
    }

    @VisibleForTesting
    public MockServerLogger(final Configuration configuration, final Logger logger) {
        this.configuration = configuration;
        this.logger = logger;
        this.httpStateHandler = null;
    }

    public MockServerLogger(final Class<?> loggerClass) {
        this.configuration = null;
        this.logger = LoggerFactory.getLogger(loggerClass);
        this.httpStateHandler = null;
    }

    public MockServerLogger(final Configuration configuration, final Class<?> loggerClass) {
        this.configuration = configuration;
        this.logger = LoggerFactory.getLogger(loggerClass);
        this.httpStateHandler = null;
    }

    public MockServerLogger(final @Nullable HttpState httpStateHandler) {
        this.configuration = null;
        this.logger = null;
        this.httpStateHandler = httpStateHandler;
    }

    public MockServerLogger(final Configuration configuration, final @Nullable HttpState httpStateHandler) {
        this.configuration = configuration;
        this.logger = null;
        this.httpStateHandler = httpStateHandler;
    }

    public MockServerLogger setHttpStateHandler(HttpState httpStateHandler) {
        this.httpStateHandler = httpStateHandler;
        return this;
    }

    public void logEvent(LogEntry logEntry) {
        if (logEntry.getType() == RECEIVED_REQUEST
            || logEntry.getType() == FORWARDED_REQUEST
            || logEntry.getType() == EXPECTATION_RESPONSE
            || logEntry.isAlwaysLog()
            || isEnabledForInstance(logEntry.getLogLevel())) {
            if (httpStateHandler != null) {
                httpStateHandler.log(logEntry);
            } else if (configuration != null) {
                writeToSystemOut(logger, logEntry, configuration);
            } else {
                writeToSystemOut(logger, logEntry);
            }
        }
    }

    public boolean isEnabledForInstance(final Level level) {
        if (configuration != null) {
            return isEnabled(level, configuration.logLevel());
        }
        return isEnabled(level, ConfigurationProperties.logLevel());
    }

    public boolean isDisableLogging() {
        if (configuration != null) {
            return configuration.disableLogging();
        }
        return ConfigurationProperties.disableLogging();
    }

    public static void writeToSystemOut(Logger logger, LogEntry logEntry, Configuration configuration) {
        if (!configuration.disableLogging()) {
            Level effectiveLevel = resolveEffectiveLevel(logEntry.getType(), configuration.logLevelOverrides(), configuration.logLevel());
            if ((logEntry.isAlwaysLog() || isEnabled(logEntry.getLogLevel(), effectiveLevel)) &&
                isNotBlank(logEntry.getMessage())) {
                writeLogEntry(logger, logEntry);
            }
        }
    }

    public static void writeToSystemOut(Logger logger, LogEntry logEntry) {
        if (!ConfigurationProperties.disableLogging()) {
            Level effectiveLevel = resolveEffectiveLevel(logEntry.getType(), ConfigurationProperties.logLevelOverrides(), ConfigurationProperties.logLevel());
            if ((logEntry.isAlwaysLog() || isEnabled(logEntry.getLogLevel(), effectiveLevel)) &&
                isNotBlank(logEntry.getMessage())) {
                writeLogEntry(logger, logEntry);
            }
        }
    }

    private static void writeLogEntry(Logger logger, LogEntry logEntry) {
        switch (logEntry.getLogLevel()) {
            case ERROR:
                logger.error(portInformation(logEntry) + logEntry.getMessage(), logEntry.getThrowable());
                break;
            case WARN:
                logger.warn(portInformation(logEntry) + logEntry.getMessage(), logEntry.getThrowable());
                break;
            case INFO:
                logger.info(portInformation(logEntry) + logEntry.getMessage(), logEntry.getThrowable());
                break;
            case DEBUG:
                logger.debug(portInformation(logEntry) + logEntry.getMessage(), logEntry.getThrowable());
                break;
            case TRACE:
                logger.trace(portInformation(logEntry) + logEntry.getMessage(), logEntry.getThrowable());
                break;
        }
    }

    private static String portInformation(LogEntry logEntry) {
        Integer port = logEntry.getPort();
        if (port != null) {
            return port + " ";
        } else {
            return "";
        }
    }

    public static boolean isEnabled(final Level level) {
        return isEnabled(level, ConfigurationProperties.logLevel());
    }

    public static boolean isEnabled(final Level level, final Level configuredLevel) {
        return configuredLevel != null && level.toInt() >= configuredLevel.toInt();
    }
}
