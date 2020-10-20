package org.mockserver.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.mockserver.model.NottableString;

/**
 * @author jamesdbloom
 */
public class NottableStringToJavaSerializer {

    public static String serialize(NottableString nottableString, boolean alwaysNottableString) {
        if (nottableString.isOptional()) {
            return "optional(\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\")";
        } else if (nottableString.isNot()) {
            return "not(\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\")";
        } else if (alwaysNottableString) {
            return "string(\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\")";
        } else {
            return "\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\"";
        }
    }

}
