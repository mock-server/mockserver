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

    public void traceLog(String message, Object... objects) {
        if (logger.isTraceEnabled()) {
            Object[] indentedObjects = new String[objects.length];
            for (int i = 0; i < objects.length; i++) {
                indentedObjects[i] = NEW_LINE + NEW_LINE + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + NEW_LINE;
            }
            logger.trace(message + NEW_LINE, indentedObjects);
        }
    }

    public void infoLog(String message, Object... objects) {
        if (logger.isInfoEnabled()) {
            Object[] indentedObjects = new String[objects.length];
            for (int i = 0; i < objects.length; i++) {
                indentedObjects[i] = NEW_LINE + NEW_LINE + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + NEW_LINE;
            }
            logger.info(message + NEW_LINE, indentedObjects);
        }
    }
}
