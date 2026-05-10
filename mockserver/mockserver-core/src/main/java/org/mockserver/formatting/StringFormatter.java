package org.mockserver.formatting;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import io.netty.buffer.ByteBufUtil;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Action;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class StringFormatter {

    private static final Map<Integer, String> INDENTS = new HashMap<>();
    private static final Splitter fixedLengthSplitter = Splitter.fixedLength(64);
    private static final Joiner newLineJoiner = Joiner.on(NEW_LINE);

    static {
        INDENTS.put(0, "");
        INDENTS.put(1, "  ");
        INDENTS.put(2, "    ");
        INDENTS.put(3, "      ");
        INDENTS.put(4, "        ");
    }

    public static StringBuilder[] indentAndToString(final Object... objects) {
        return indentAndToString(1, objects);
    }

    public static StringBuilder[] indentAndToString(final int indent, final Object... objects) {
        final StringBuilder[] indentedObjects = new StringBuilder[objects.length];
        for (int i = 0; i < objects.length; i++) {
            indentedObjects[i] =
                new StringBuilder(NEW_LINE)
                    .append(NEW_LINE)
                    .append(String.valueOf(objects[i]).replaceAll("(?m)^", INDENTS.get(indent)))
                    .append(NEW_LINE);
        }
        return indentedObjects;
    }

    public static String formatLogMessage(final String message, final Object... arguments) {
        return formatLogMessage(0, message, arguments);
    }

    public static String formatLogMessage(final int indent, final String message, final Object... arguments) {
        final StringBuilder logMessage = new StringBuilder();
        final StringBuilder[] formattedArguments = indentAndToString(indent + 1, arguments);
        final String[] messageParts = message.split("\\{}");
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(INDENTS.get(indent)).append(messageParts[messagePartIndex]);
            if (formattedArguments.length > 0 &&
                formattedArguments.length > messagePartIndex) {
                logMessage.append(formattedArguments[messagePartIndex]);
            }
            if (messagePartIndex < messageParts.length - 1) {
                logMessage.append(NEW_LINE);
                if (!messageParts[messagePartIndex + 1].startsWith(" ")) {
                    logMessage.append(" ");
                }
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

    public static String formatCompactLogMessage(final String message, final Object... arguments) {
        final String[] messageParts = message.split("\\{}");
        final StringBuilder logMessage = new StringBuilder();
        for (int i = 0; i < messageParts.length; i++) {
            String part = messageParts[i].trim();
            if (i > 0 && logMessage.length() > 0 && !part.isEmpty()) {
                logMessage.append(" ");
            }
            logMessage.append(part);
            if (arguments != null && i < arguments.length) {
                String compact = toCompactString(arguments[i]);
                if (!compact.isEmpty()) {
                    if (logMessage.length() > 0) {
                        logMessage.append(" ");
                    }
                    logMessage.append(compact);
                }
            }
        }
        return logMessage.toString();
    }

    @SuppressWarnings("rawtypes")
    public static String toCompactString(Object argument) {
        if (argument == null) {
            return "";
        }
        if (argument instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) argument;
            Integer statusCode = response.getStatusCode();
            return statusCode != null ? String.valueOf(statusCode) : "response";
        }
        if (argument instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) argument;
            String method = request.getMethod("");
            String path = request.getPath() != null ? request.getPath().getValue() : "";
            if (isNotBlank(method) && isNotBlank(path)) {
                return method + " " + path;
            } else if (isNotBlank(path)) {
                return path;
            } else if (isNotBlank(method)) {
                return method;
            }
            return "request";
        }
        if (argument instanceof Expectation) {
            return ((Expectation) argument).getId();
        }
        if (argument instanceof Action) {
            Action<?> action = (Action<?>) argument;
            Action.Type type = action.getType();
            return type != null ? type.name().toLowerCase() : "action";
        }
        String str = String.valueOf(argument);
        str = str.replaceAll("\\s*\\n\\s*", " ").trim();
        if (str.length() > 120) {
            str = str.substring(0, 117) + "...";
        }
        return str;
    }

    public static String formatBytes(byte[] bytes) {
        return newLineJoiner.join(fixedLengthSplitter.split(ByteBufUtil.hexDump(bytes)));
    }
}
