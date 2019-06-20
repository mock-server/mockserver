package org.mockserver.logging;

import com.google.common.collect.ImmutableList;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandles;
import java.util.List;

import static java.lang.invoke.MethodType.methodType;
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
        try {
            setRootLogLevel("io.netty", System.getProperty("root.logLevel", "WARN"));
            setRootLogLevel("org.apache.velocity", System.getProperty("root.logLevel", "WARN"));
            setRootLogLevel("org.mockserver", System.getProperty("mockserver.logLevel", logLevel().name()));
        } catch (Throwable throwable) {
            LoggerFactory.getLogger(MockServerLogger.class).debug("exception while initialising log levels please include ch.qos.logback:logback-classic dependency to enable log file support", throwable);
        }
    }

    public static void setRootLogLevel(String name, String level) {
        try {
            Logger logger = LoggerFactory.getLogger(name);
            Class loggerClass = Class.forName("ch.qos.logback.classic.Logger");
            Class levelClass = Class.forName("ch.qos.logback.classic.Level");
            MethodHandles
                .publicLookup()
                .findVirtual(loggerClass, "setLevel", methodType(void.class, levelClass))
                .invoke(
                    logger,
                    MethodHandles
                        .publicLookup()
                        .findStatic(levelClass, "valueOf", methodType(levelClass, String.class))
                        .invoke(level)
                );
        } catch (Throwable throwable) {
            LoggerFactory.getLogger(MockServerLogger.class).debug("exception while setting log level for " + name + " please include ch.qos.logback:logback-classic dependency to enable log file support", throwable);
        }
    }

    private static void setLogbackAppender() {
        try {
            Logger mockServerLogger = LoggerFactory.getLogger("org.mockserver");
            Class<?> loggerClass = Class.forName("ch.qos.logback.classic.Logger");
            if (mockServerLogger.getClass().isAssignableFrom(loggerClass)) {
                ILoggerFactory loggerContext = LoggerFactory.getILoggerFactory();
                Class contextClass = Class.forName("ch.qos.logback.core.Context");

                // ----------------------
                // PatternLayoutEncoder
                // ----------------------
                Class patternLayoutEncoderClass = Class.forName("ch.qos.logback.classic.encoder.PatternLayoutEncoder");
                Object patternLayoutEncoder = MethodHandles
                    .publicLookup()
                    .findConstructor(patternLayoutEncoderClass, methodType(void.class))
                    .invoke();
                // setPattern
                MethodHandles
                    .publicLookup()
                    .findVirtual(patternLayoutEncoderClass, "setPattern", methodType(void.class, String.class))
                    .invoke(patternLayoutEncoder, "%date %level %logger{20} %msg%n");
                // setContext
                MethodHandles
                    .publicLookup()
                    .findVirtual(patternLayoutEncoderClass, "setContext", methodType(void.class, contextClass))
                    .invoke(patternLayoutEncoder, contextClass.cast(loggerContext));
                // start
                MethodHandles
                    .publicLookup()
                    .findVirtual(patternLayoutEncoderClass, "start", methodType(void.class))
                    .invoke(patternLayoutEncoder);

                String logDirectory = isBlank(System.getenv("log.dir")) ? "./" : appendIfMissingIgnoreCase(System.getenv("log.dir"), "/");
                String logFileName = "mockserver";
                String logFileExtension = "log";

                // ----------------------
                // SizeAndTimeBasedFNATP
                // ----------------------
                Class sizeAndTimeBasedFNATPClass = Class.forName("ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP");
                Object sizeAndTimeBasedFNATP = MethodHandles
                    .publicLookup()
                    .findConstructor(sizeAndTimeBasedFNATPClass, methodType(void.class))
                    .invoke();
                // setMaxFileSize
                MethodHandles
                    .publicLookup()
                    .findVirtual(sizeAndTimeBasedFNATPClass, "setMaxFileSize", methodType(void.class, String.class))
                    .invoke(sizeAndTimeBasedFNATP, "100MB");
                // setContext
                MethodHandles
                    .publicLookup()
                    .findVirtual(sizeAndTimeBasedFNATPClass, "setContext", methodType(void.class, contextClass))
                    .invoke(sizeAndTimeBasedFNATP, contextClass.cast(loggerContext));

                // ----------------------
                // TimeBasedRollingPolicy
                // ----------------------
                Class timeBasedRollingPolicyClass = Class.forName("ch.qos.logback.core.rolling.TimeBasedRollingPolicy");
                Object timeBasedRollingPolicy = MethodHandles
                    .publicLookup()
                    .findConstructor(timeBasedRollingPolicyClass, methodType(void.class))
                    .invoke();
                // setFileNamePattern
                MethodHandles
                    .publicLookup()
                    .findVirtual(timeBasedRollingPolicyClass, "setFileNamePattern", methodType(void.class, String.class))
                    .invoke(timeBasedRollingPolicy, logDirectory + logFileName + ".%d{yyyy-MM-dd}.%i." + logFileExtension);
                // setTimeBasedFileNamingAndTriggeringPolicy
                Class<?> timeBasedFileNamingAndTriggeringPolicyClass = Class.forName("ch.qos.logback.core.rolling.TimeBasedFileNamingAndTriggeringPolicy");
                MethodHandles
                    .publicLookup()
                    .findVirtual(timeBasedRollingPolicyClass, "setTimeBasedFileNamingAndTriggeringPolicy", methodType(void.class, timeBasedFileNamingAndTriggeringPolicyClass))
                    .invoke(timeBasedRollingPolicy, timeBasedFileNamingAndTriggeringPolicyClass.cast(sizeAndTimeBasedFNATP));
                // setMaxHistory
                MethodHandles
                    .publicLookup()
                    .findVirtual(timeBasedRollingPolicyClass, "setMaxHistory", methodType(void.class, int.class))
                    .invoke(timeBasedRollingPolicy, 1);
                // setContext
                MethodHandles
                    .publicLookup()
                    .findVirtual(timeBasedRollingPolicyClass, "setContext", methodType(void.class, contextClass))
                    .invoke(timeBasedRollingPolicy, contextClass.cast(loggerContext));

                // -------------------
                // RollingFileAppender
                // -------------------
                Class rollingFileAppenderClass = Class.forName("ch.qos.logback.core.rolling.RollingFileAppender");
                Object rollingFileAppender = MethodHandles
                    .publicLookup()
                    .findConstructor(rollingFileAppenderClass, methodType(void.class))
                    .invoke();
                // setFile
                MethodHandles
                    .publicLookup()
                    .findVirtual(rollingFileAppenderClass, "setFile", methodType(void.class, String.class))
                    .invoke(rollingFileAppender, logDirectory + logFileName + "." + logFileExtension);
                // setEncoder
                Class<?> encoderClass = Class.forName("ch.qos.logback.core.encoder.Encoder");
                MethodHandles
                    .publicLookup()
                    .findVirtual(rollingFileAppenderClass, "setEncoder", methodType(void.class, encoderClass))
                    .invoke(rollingFileAppender, encoderClass.cast(patternLayoutEncoder));
                // setContext
                MethodHandles
                    .publicLookup()
                    .findVirtual(rollingFileAppenderClass, "setContext", methodType(void.class, contextClass))
                    .invoke(rollingFileAppender, contextClass.cast(loggerContext));

                // timeBasedRollingPolicy_setParent
                Class fileAppenderClass = Class.forName("ch.qos.logback.core.FileAppender");
                MethodHandles
                    .publicLookup()
                    .findVirtual(timeBasedRollingPolicyClass, "setParent", methodType(void.class, fileAppenderClass))
                    .invoke(timeBasedRollingPolicy, fileAppenderClass.cast(rollingFileAppender));
                // fileAppender_setRollingPolicy
                Class rollingPolicyClass = Class.forName("ch.qos.logback.core.rolling.RollingPolicy");
                MethodHandles
                    .publicLookup()
                    .findVirtual(rollingFileAppenderClass, "setRollingPolicy", methodType(void.class, rollingPolicyClass))
                    .invoke(rollingFileAppender, rollingPolicyClass.cast(timeBasedRollingPolicy));
                // timeBasedRollingPolicy_start
                MethodHandles
                    .publicLookup()
                    .findVirtual(timeBasedRollingPolicyClass, "start", methodType(void.class))
                    .invoke(timeBasedRollingPolicy);
                // sizeAndTimeBasedFNATP_start
                MethodHandles
                    .publicLookup()
                    .findVirtual(sizeAndTimeBasedFNATPClass, "start", methodType(void.class))
                    .invoke(sizeAndTimeBasedFNATP);
                // fileAppender_start
                MethodHandles
                    .publicLookup()
                    .findVirtual(rollingFileAppenderClass, "start", methodType(void.class))
                    .invoke(rollingFileAppender);

                // -------------
                // AsyncAppender
                // -------------
                Class asyncAppenderClass = Class.forName("ch.qos.logback.classic.AsyncAppender");
                Object asyncAppender = MethodHandles
                    .publicLookup()
                    .findConstructor(asyncAppenderClass, methodType(void.class))
                    .invoke();
                // setContext
                MethodHandles
                    .publicLookup()
                    .findVirtual(asyncAppenderClass, "setContext", methodType(void.class, contextClass))
                    .invoke(asyncAppender, contextClass.cast(loggerContext));
                // setQueueSize -> int
                MethodHandles
                    .publicLookup()
                    .findVirtual(asyncAppenderClass, "setQueueSize", methodType(void.class, int.class))
                    .invoke(asyncAppender, 250);
                // setDiscardingThreshold -> int
                MethodHandles
                    .publicLookup()
                    .findVirtual(asyncAppenderClass, "setDiscardingThreshold", methodType(void.class, int.class))
                    .invoke(asyncAppender, 0);
                // addAppender -> ch.qos.logback.core.Appender
                Class appenderClass = Class.forName("ch.qos.logback.core.Appender");
                MethodHandles
                    .publicLookup()
                    .findVirtual(asyncAppenderClass, "addAppender", methodType(void.class, appenderClass))
                    .invoke(asyncAppender, appenderClass.cast(rollingFileAppender));
                // start
                MethodHandles
                    .publicLookup()
                    .findVirtual(asyncAppenderClass, "start", methodType(void.class))
                    .invoke(asyncAppender);

                // setLevel
                Class levelClass = Class.forName("ch.qos.logback.classic.Level");
                Object level = MethodHandles
                    .publicLookup()
                    .findStatic(levelClass, "valueOf", methodType(levelClass, String.class))
                    .invoke(System.getProperty("mockserver.logLevel", logLevel().name()));
                MethodHandles
                    .publicLookup()
                    .findVirtual(loggerClass, "setLevel", methodType(void.class, levelClass))
                    .invoke(mockServerLogger, level);
                // addAppender
                MethodHandles
                    .publicLookup()
                    .findVirtual(loggerClass, "addAppender", methodType(void.class, appenderClass))
                    .invoke(mockServerLogger, appenderClass.cast(asyncAppender));
            }
        } catch (Throwable throwable) {
            LoggerFactory.getLogger(MockServerLogger.class).debug("exception while initialising log file please include ch.qos.logback:logback-classic dependency to enable log file support", throwable);
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
