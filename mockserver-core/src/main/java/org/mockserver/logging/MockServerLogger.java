package org.mockserver.logging;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.google.common.collect.ImmutableList;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.appendIfMissingIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.configuration.ConfigurationProperties.logLevel;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.EXCEPTION;
import static org.mockserver.model.HttpRequest.request;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class MockServerLogger {

    public static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger();

    static {
        initialiseLogLevels();
        setLogbackAppender();
    }

    public static void initialiseLogLevels() {
        setRootLogLevel("io.netty");
        setRootLogLevel("org.apache.velocity");
        Logger mockServerLogger = LoggerFactory.getLogger("org.mockserver");
        try {
            Class.forName("ch.qos.logback.classic.Logger");
            if (mockServerLogger instanceof ch.qos.logback.classic.Logger) {
                ch.qos.logback.classic.Logger mockServerLogbackLogger = (ch.qos.logback.classic.Logger) mockServerLogger;
                mockServerLogbackLogger.setLevel(
                    ch.qos.logback.classic.Level.valueOf(System.getProperty("mockserver.logLevel", logLevel().name()))
                );
            }
        } catch (ClassNotFoundException ignore) {
        }
    }

    private static void setLogbackAppender() {
        Logger mockServerLogger = LoggerFactory.getLogger("org.mockserver");
        try {
            Class.forName("ch.qos.logback.classic.Logger");
            if (mockServerLogger instanceof ch.qos.logback.classic.Logger) {
                LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

                PatternLayoutEncoder patternLayoutEncoder = new PatternLayoutEncoder();
                patternLayoutEncoder.setPattern("%date %level %logger{20} %msg%n");
                patternLayoutEncoder.setContext(loggerContext);
                patternLayoutEncoder.start();

                String logDirectory = isBlank(System.getenv("log.dir")) ? "./" : appendIfMissingIgnoreCase(System.getenv("log.dir"), "/");
                String logFileName = "mockserver";
                String logFileExtension = "log";

                SizeAndTimeBasedFNATP<SizeAndTimeBasedFNATP> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<>();
                sizeAndTimeBasedFNATP.setMaxFileSize("100MB");
                sizeAndTimeBasedFNATP.setContext(loggerContext);

                TimeBasedRollingPolicy<SizeAndTimeBasedFNATP> timeBasedRollingPolicy = new TimeBasedRollingPolicy<>();
                timeBasedRollingPolicy.setFileNamePattern(logDirectory + logFileName + ".%d{yyyy-MM-dd}.%i." + logFileExtension);
                timeBasedRollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(sizeAndTimeBasedFNATP);
                timeBasedRollingPolicy.setMaxHistory(1);
                timeBasedRollingPolicy.setContext(loggerContext);

                RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
                fileAppender.setFile(logDirectory + logFileName + "." + logFileExtension);
                fileAppender.setEncoder(patternLayoutEncoder);
                fileAppender.setContext(loggerContext);
                timeBasedRollingPolicy.setParent(fileAppender);
                fileAppender.setRollingPolicy(timeBasedRollingPolicy);
                timeBasedRollingPolicy.start();
                sizeAndTimeBasedFNATP.start();
                fileAppender.start();

                AsyncAppender asyncAppender = new AsyncAppender();
                asyncAppender.setContext(loggerContext);
                asyncAppender.setQueueSize(250);
                asyncAppender.setDiscardingThreshold(0);
                asyncAppender.addAppender(fileAppender);
                asyncAppender.start();

                ch.qos.logback.classic.Logger mockServerLogbackLogger = (ch.qos.logback.classic.Logger) mockServerLogger;
                mockServerLogbackLogger.setLevel(
                    ch.qos.logback.classic.Level.valueOf(System.getProperty("mockserver.logLevel", logLevel().name()))
                );
                mockServerLogbackLogger.addAppender(fileAppender);
            }
        } catch (ClassNotFoundException ignore) {
        }
    }

    private final boolean auditEnabled = !ConfigurationProperties.disableRequestAudit();
    private final boolean logEnabled = !ConfigurationProperties.disableSystemOut();
    private final Logger logger;
    private final HttpStateHandler httpStateHandler;

    public MockServerLogger() {
        this(MockServerLogger.class);
    }

    public MockServerLogger(final Class loggerClass) {
        this(LoggerFactory.getLogger(loggerClass), null);
    }

    public MockServerLogger(final Logger logger, final @Nullable HttpStateHandler httpStateHandler) {
        this.logger = logger;
        this.httpStateHandler = httpStateHandler;
    }

    public static void setRootLogLevel(String name) {
        Logger logger = LoggerFactory.getLogger(name);
        try {
            Class.forName("ch.qos.logback.classic.Logger");
            if (logger instanceof ch.qos.logback.classic.Logger) {
                ((ch.qos.logback.classic.Logger) logger).setLevel(
                    ch.qos.logback.classic.Level.valueOf(System.getProperty("root.logLevel", "WARN"))
                );
            }
        } catch (ClassNotFoundException ignore) {
        }
    }

    public void trace(final String message, final Object... arguments) {
        trace(null, message, arguments);
    }

    public void trace(final HttpRequest request, final String message, final Object... arguments) {
        if (isEnabled(TRACE)) {
            addLogEvents(MessageLogEntry.LogMessageType.TRACE, TRACE, request, message, arguments);
            final String logMessage = formatLogMessage(message, arguments);
            if (logEnabled) {
                logger.trace(logMessage);
            }
        }
    }

    public void debug(final MessageLogEntry.LogMessageType type, final String message, final Object... arguments) {
        debug(type, null, message, arguments);
    }

    public void debug(final MessageLogEntry.LogMessageType type, final HttpRequest request, final String message, final Object... arguments) {
        if (isEnabled(DEBUG)) {
            addLogEvents(type, DEBUG, request, message, arguments);
            final String logMessage = formatLogMessage(message, arguments);
            if (logEnabled) {
                logger.debug(logMessage);
            }
        }
    }

    public void info(final MessageLogEntry.LogMessageType type, final String message, final Object... arguments) {
        info(type, (HttpRequest) null, message, arguments);
    }

    public void info(final MessageLogEntry.LogMessageType type, final HttpRequest request, final String message, final Object... arguments) {
        info(type, ImmutableList.of(request != null ? request : request()), message, arguments);
    }

    public void info(final MessageLogEntry.LogMessageType type, final List<HttpRequest> requests, final String message, final Object... arguments) {
        if (isEnabled(INFO)) {
            addLogEvents(type, INFO, requests, message, arguments);
            final String logMessage = formatLogMessage(message, arguments);
            if (logEnabled) {
                logger.info(logMessage);
            }
        }
    }

    public void warn(final String message) {
        warn((HttpRequest) null, message);
    }

    public void warn(final String message, final Object... arguments) {
        warn(null, message, arguments);
    }

    public void warn(final @Nullable HttpRequest request, final String message, final Object... arguments) {
        if (isEnabled(WARN)) {
            addLogEvents(MessageLogEntry.LogMessageType.WARN, WARN, request, message, arguments);
            final String logMessage = formatLogMessage(message, arguments);
            if (logEnabled) {
                logger.error(logMessage);
            }
        }
    }

    public void error(final String message, final Throwable throwable) {
        error((HttpRequest) null, throwable, message);
    }

    public void error(final String message, final Object... arguments) {
        error(null, message, arguments);
    }

    public void error(final @Nullable HttpRequest request, final String message, final Object... arguments) {
        error(request, null, message, arguments);
    }

    public void error(final @Nullable HttpRequest request, final Throwable throwable, final String message, final Object... arguments) {
        error(ImmutableList.of(request != null ? request : request()), throwable, message, arguments);
    }

    public void error(final List<HttpRequest> requests, final Throwable throwable, final String message, final Object... arguments) {
        if (isEnabled(ERROR)) {
            addLogEvents(EXCEPTION, ERROR, requests, message, arguments);
            final String logMessage = formatLogMessage(message, arguments);
            if (logEnabled) {
                logger.error(logMessage, throwable);
            }
        }
    }

    private void addLogEvents(final MessageLogEntry.LogMessageType type, final Level logLeveL, final @Nullable HttpRequest request, final String message, final Object... arguments) {
        if (auditEnabled && httpStateHandler != null) {
            httpStateHandler.log(new MessageLogEntry(type, logLeveL, request, message, arguments));
        }
    }

    private void addLogEvents(final MessageLogEntry.LogMessageType type, final Level logLeveL, final List<HttpRequest> requests, final String message, final Object... arguments) {
        if (auditEnabled && httpStateHandler != null) {
            httpStateHandler.log(new MessageLogEntry(type, logLeveL, requests, message, arguments));
        }
    }

    public boolean isEnabled(final Level level) {
        return level.toInt() >= logLevel().toInt();
    }
}
