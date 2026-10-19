package org.mockserver.logging;

import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.helpers.NOPLogger;
import org.slf4j.helpers.SubstituteLogger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.logging.Handler;

/**
 * Tells whether a message logged through an SLF4J {@link Logger} would be printed anywhere, so a caller can skip
 * rendering one that would not. It can tell only for SLF4J's no-operation logger (no provider) and for the
 * java.util.logging provider MockServer bundles (slf4j-jdk14, relocated or not); for any other provider, or a
 * logger it cannot inspect, it answers that the message may be printed.
 */
final class LogOutputProbe {

    private static final String JUL_ADAPTER_SUFFIX = ".jul.JDK14LoggerAdapter";

    private static final ClassValue<Optional<MethodHandle>> JUL_LOGGER = new ClassValue<>() {
        @Override
        protected Optional<MethodHandle> computeValue(Class<?> type) {
            if (!type.getName().endsWith(JUL_ADAPTER_SUFFIX)) {
                return Optional.empty();
            }
            try {
                Field field = type.getDeclaredField("logger");
                if (field.getType() != java.util.logging.Logger.class) {
                    return Optional.empty();
                }
                field.setAccessible(true);
                return Optional.of(MethodHandles.lookup().unreflectGetter(field));
            } catch (Throwable throwable) {
                return Optional.empty();
            }
        }
    };

    private LogOutputProbe() {
    }

    /**
     * True only when {@code logger} is SLF4J's no-operation logger, or is backed by a java.util.logging logger
     * that would print a message at {@code level} through no handler: the level is below the logger's, or no
     * handler is attached to it or to any parent it passes records to. A logger with a filter is taken to print,
     * since a filter sees each record, and so is any chain holding a logger of a class other than the JDK's own:
     * a custom log manager's logger, such as log4j-jul's, can print without handlers.
     */
    static boolean discards(Logger logger, Level level) {
        if (logger instanceof SubstituteLogger) {
            logger = ((SubstituteLogger) logger).delegate();
        }
        if (logger != null && logger.getClass() == NOPLogger.class) {
            return true;
        }
        java.util.logging.Logger julLogger = julLogger(logger);
        if (julLogger == null || level == null) {
            return false;
        }
        if (!julLogger.isLoggable(julLevel(level))) {
            return true;
        }
        if (julLogger.getFilter() != null) {
            return false;
        }
        for (java.util.logging.Logger current = julLogger; current != null; current = current.getParent()) {
            if (current.getClass().getClassLoader() != null) {
                return false;
            }
            Handler[] handlers = current.getHandlers();
            if (handlers.length > 0) {
                return false;
            }
            if (!current.getUseParentHandlers()) {
                return true;
            }
        }
        return true;
    }

    private static java.util.logging.Logger julLogger(Logger logger) {
        if (logger == null) {
            return null;
        }
        Optional<MethodHandle> getter = JUL_LOGGER.get(logger.getClass());
        if (getter.isEmpty()) {
            return null;
        }
        try {
            return (java.util.logging.Logger) getter.get().invoke(logger);
        } catch (Throwable throwable) {
            return null;
        }
    }

    // as slf4j-jdk14 maps each level
    private static java.util.logging.Level julLevel(Level level) {
        switch (level) {
            case TRACE:
                return java.util.logging.Level.FINEST;
            case DEBUG:
                return java.util.logging.Level.FINE;
            case INFO:
                return java.util.logging.Level.INFO;
            case WARN:
                return java.util.logging.Level.WARNING;
            default:
                return java.util.logging.Level.SEVERE;
        }
    }
}
