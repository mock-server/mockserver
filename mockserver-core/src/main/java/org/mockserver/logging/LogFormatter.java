package org.mockserver.logging;

import org.slf4j.Logger;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.indentAndToString;

/**
 * @author jamesdbloom
 */
public class LogFormatter {

    private final Logger logger;

    public LogFormatter(Logger logger) {
        this.logger = logger;
    }

    public void traceLog(String message, Object... arguments) {
        if (logger.isTraceEnabled()) {
            logger.trace(message + NEW_LINE, indentAndToString(arguments));
        }
    }

    public void infoLog(String message, Object... arguments) {
        if (logger.isInfoEnabled()) {
            logger.info(message + NEW_LINE, indentAndToString(arguments));
        }
    }

    public void errorLog(String message, Object... arguments) {
        logger.error(formatLogMessage(message, arguments).toString());
    }

    public void errorLog(Throwable throwable, String message, Object... arguments) {
        logger.error(formatLogMessage(message, arguments).toString(), throwable);
    }

    public StringBuilder formatLogMessage(String message, Object... arguments) {
        StringBuilder errorMessage = new StringBuilder();
        Object[] formattedArguments = indentAndToString(arguments);
        String[] messageParts = (message + NEW_LINE).split("\\{\\}");
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            errorMessage.append(messageParts[messagePartIndex]);
            if (formattedArguments.length > messagePartIndex) {
                errorMessage.append(formattedArguments[messagePartIndex]);
            }
        }
        return errorMessage;
    }

}
