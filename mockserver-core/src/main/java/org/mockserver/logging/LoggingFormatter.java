package org.mockserver.logging;

import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;

import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;

/**
 * @author jamesdbloom
 */
public class LoggingFormatter {

    private final Logger logger;
    private final HttpStateHandler httpStateHandler;

    public LoggingFormatter(Logger logger, HttpStateHandler httpStateHandler) {
        this.logger = logger;
        this.httpStateHandler = httpStateHandler;
    }

    public void traceLog(String message, Object... arguments) {
        if (logger.isTraceEnabled()) {
            logger.trace(formatLogMessage(message + NEW_LINE + NEW_LINE, arguments));
        }
    }

    public void infoLog(HttpRequest request, String message, Object... arguments) {
        String logMessage = formatLogMessage(message + NEW_LINE, arguments);
        logger.info(logMessage);
        addLogEvents(request, logMessage);
    }

    public void infoLog(List<HttpRequest> requests, String message, Object... arguments) {
        String logMessage = formatLogMessage(message + NEW_LINE, arguments);
        logger.info(logMessage);
        addLogEvents(requests, logMessage);
    }

    public void errorLog(HttpRequest request, String message, Object... arguments) {
        String logMessage = formatLogMessage(message, arguments);
        logger.error(logMessage);
        addLogEvents(request, logMessage);
    }

    public void errorLog(HttpRequest request, Throwable throwable, String message, Object... arguments) {
        String logMessage = formatLogMessage(message, arguments);
        logger.error(logMessage, throwable);
        addLogEvents(request, logMessage);
    }

    public void errorLog(List<HttpRequest> requests, Throwable throwable, String message, Object... arguments) {
        String logMessage = formatLogMessage(message, arguments);
        logger.error(logMessage, throwable);
        addLogEvents(requests, logMessage);
    }

    private void addLogEvents(HttpRequest request, String logMessage) {
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


}
