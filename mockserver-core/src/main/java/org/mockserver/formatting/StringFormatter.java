package org.mockserver.formatting;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class StringFormatter {

    public static StringBuilder[] indentAndToString(final Object... objects) {
        final StringBuilder[] indentedObjects = new StringBuilder[objects.length];
        for (int i = 0; i < objects.length; i++) {
            indentedObjects[i] =
                new StringBuilder(NEW_LINE)
                    .append(NEW_LINE)
                    .append(String.valueOf(objects[i]).replaceAll("(?m)^", "\t"))
                    .append(NEW_LINE);
        }
        return indentedObjects;
    }

    public static String formatLogMessage(final String message, final Object... arguments) {
        final StringBuilder logMessage = new StringBuilder();
        final StringBuilder[] formattedArguments = indentAndToString(arguments);
        final String[] messageParts = message.split("\\{\\}");
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(messageParts[messagePartIndex]);
            if (formattedArguments.length > 0 &&
                formattedArguments.length > messagePartIndex) {
                logMessage.append(formattedArguments[messagePartIndex]);
            }
        }
        return logMessage.toString();
    }

    public static String formatLogMessage(final String[] messageParts, final Object... arguments) {
        final StringBuilder logMessage = new StringBuilder();
        final StringBuilder[] formattedArguments = indentAndToString(arguments);
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(messageParts[messagePartIndex]);
            if (formattedArguments.length > 0 &&
                formattedArguments.length > messagePartIndex) {
                logMessage.append(formattedArguments[messagePartIndex]);
            }
        }
        return logMessage.toString();
    }
}
