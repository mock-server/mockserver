package org.mockserver.logging;

import org.slf4j.Logger;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;

/**
 * @author jamesdbloom
 */
public class LoggingFormatter {

    private final Logger logger;

    public LoggingFormatter(Logger logger) {
        this.logger = logger;
    }

    public void traceLog(String message, Object... arguments) {
        if (logger.isTraceEnabled()) {
            logger.trace(formatLogMessage(message + NEW_LINE + NEW_LINE, arguments));
        }
    }

    public void infoLog(String message, Object... arguments) {
        if (logger.isInfoEnabled()) {
            logger.info(formatLogMessage(message + NEW_LINE, arguments));
        }
    }

    public void errorLog(String message, Object... arguments) {
        logger.error(formatLogMessage(message, arguments));
    }

    public void errorLog(Throwable throwable, String message, Object... arguments) {
        logger.error(formatLogMessage(message, arguments), throwable);
    }


}
