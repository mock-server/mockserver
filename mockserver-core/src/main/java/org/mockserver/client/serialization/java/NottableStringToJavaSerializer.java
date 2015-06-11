package org.mockserver.client.serialization.java;

import org.mockserver.model.NottableString;

/**
 * @author jamesdbloom
 */
public class NottableStringToJavaSerializer {

    public static String serializeNottableString(NottableString nottableString) {
        if (nottableString.isNot()) {
            return "not(\"" + nottableString.getValue() + "\")";
        } else {
            return "\"" + nottableString.getValue() + "\"";
        }
    }
}
