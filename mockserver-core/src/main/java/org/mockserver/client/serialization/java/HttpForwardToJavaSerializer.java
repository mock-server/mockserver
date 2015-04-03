package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.model.HttpForward;

/**
 * @author jamesdbloom
 */
public class HttpForwardToJavaSerializer implements ToJavaSerializer<HttpForward> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpForward httpForward) {
        StringBuffer output = new StringBuffer();
        if (httpForward != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent, output).append("forward()");
            if (httpForward.getHost() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent + 8, output).append(".withHost(\"").append(httpForward.getHost()).append("\")");
            }
            if (httpForward.getPort() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent + 8, output).append(".withPort(").append(httpForward.getPort()).append(")");
            }
            if (httpForward.getScheme() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent + 8, output).append(".withScheme(HttpForward.Scheme.").append(httpForward.getScheme()).append(")");
            }
        }
        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(System.getProperty("line.separator")).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
