package org.mockserver.logging;

import org.slf4j.Logger;

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
                indentedObjects[i] = System.getProperty("line.separator") + System.getProperty("line.separator") + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + System.getProperty("line.separator");
            }
            logger.trace(message + System.getProperty("line.separator"), indentedObjects);
        }
    }

    public void infoLog(String message, Object... objects) {
        if (logger.isInfoEnabled()) {
            Object[] indentedObjects = new String[objects.length];
            for (int i = 0; i < objects.length; i++) {
                indentedObjects[i] = System.getProperty("line.separator") + System.getProperty("line.separator") + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + System.getProperty("line.separator");
            }
            logger.info(message + System.getProperty("line.separator"), indentedObjects);
        }
    }
}
