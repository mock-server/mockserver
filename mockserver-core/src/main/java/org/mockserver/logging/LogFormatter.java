package org.mockserver.logging;

import org.slf4j.Logger;

import static org.mockserver.character.Character.NEW_LINE;

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
            logger.trace(message + NEW_LINE, formatArguments(arguments));
        }
    }

    public void infoLog(String message, Object... arguments) {
        if (logger.isInfoEnabled()) {
            logger.info(message + NEW_LINE, formatArguments(arguments));
        }
    }

    public void errorLog(Throwable throwable, String message, Object... arguments) {
        StringBuilder errorMessage = new StringBuilder();
        Object[] formattedArguments = formatArguments(arguments);
        String[] messageParts = (message + NEW_LINE).split("\\{\\}");
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            errorMessage.append(messageParts[messagePartIndex]);
            if (formattedArguments.length > messagePartIndex) {
                errorMessage.append(formattedArguments[messagePartIndex]);
            }
        }
        logger.error(errorMessage.toString(), throwable);
    }

    private Object[] formatArguments(Object[] objects) {
        Object[] indentedObjects = new String[objects.length];
        for (int i = 0; i < objects.length; i++) {
            indentedObjects[i] = NEW_LINE + NEW_LINE + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + NEW_LINE;
        }
        return indentedObjects;
    }
}
