package org.mockserver.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.model.NottableOptionalString;
import org.mockserver.model.NottableString;

/**
 * @author jamesdbloom
 */
public class NottableStringToJavaSerializer {

    public static String serialize(NottableString nottableString, boolean alwaysNottableString) {
        if (nottableString instanceof NottableDateTimeString) {
            return serializeDateTime((NottableDateTimeString) nottableString);
        } else if (nottableString.isOptional()) {
            // as below, the single-argument factory would re-read a leading marker character
            return startsWithMarkerCharacter(nottableString)
                ? "optional(\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\", " + nottableString.isNot() + ")"
                : "optional(\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\")";
        } else if (nottableString.isNot()) {
            return "not(\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\")";
        } else if (startsWithMarkerCharacter(nottableString)) {
            // a bare string literal here would be re-read by string(String) as a negation/optional
            // marker, so the generated code would mean the opposite of the expectation it came from.
            // The two-argument form takes the value verbatim.
            return "string(\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\", false)";
        } else if (alwaysNottableString) {
            return "string(\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\")";
        } else {
            return "\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\"";
        }
    }

    /**
     * True when any of the values can only be written as a {@link NottableString} expression, so the key
     * must be one too for the generated constructor call to compile.
     */
    static boolean needsNottableKey(Iterable<NottableString> values) {
        for (NottableString value : values) {
            if (value instanceof NottableDateTimeString) {
                return true;
            }
        }
        return false;
    }

    private static String serializeDateTime(NottableDateTimeString dateTime) {
        StringBuilder output = new StringBuilder("NottableDateTimeString.")
            .append(dateTime.getOperator().fieldName())
            .append("(\"").append(StringEscapeUtils.escapeJava(dateTime.getExpected())).append("\")");
        appendOption(output, "withActualFormat", dateTime.getActualFormat());
        appendOption(output, "withTruncateExpected", dateTime.getTruncateExpected());
        appendOption(output, "withTruncateActual", dateTime.getTruncateActual());
        if (dateTime.getApplyTruncationLast() != null) {
            output.append(".withApplyTruncationLast(").append(dateTime.getApplyTruncationLast()).append(")");
        }
        appendOption(output, "withZone", dateTime.getZone());
        if (dateTime.isNot()) {
            output.append(".withNot(true)");
        }
        return output.toString();
    }

    private static void appendOption(StringBuilder output, String method, String value) {
        if (value != null) {
            output.append('.').append(method).append("(\"").append(StringEscapeUtils.escapeJava(value)).append("\")");
        }
    }

    /**
     * True when the value's own first character is the negation ('!') or optional ('?') marker, so
     * the single-argument {@code string(...)} / {@code optional(...)} factories would strip it and
     * change the meaning of the generated code.
     */
    private static boolean startsWithMarkerCharacter(NottableString nottableString) {
        String value = nottableString.getValue();
        return value != null
            && !value.isEmpty()
            && (value.charAt(0) == NottableString.NOT_CHAR || value.charAt(0) == NottableOptionalString.OPTIONAL_CHAR);
    }

}
