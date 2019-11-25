package org.mockserver.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.mockserver.model.NottableString;

/**
 * @author jamesdbloom
 */
public class NottableStringToJavaSerializer {

    public static String serialize(NottableString nottableString) {
        if (nottableString.isNot()) {
            return "not(\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\")";
        } else {
            return "\"" + StringEscapeUtils.escapeJava(nottableString.getValue()) + "\"";
        }
    }
}
