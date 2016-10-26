package org.mockserver.client.serialization.java;

import static org.mockserver.client.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

import org.mockserver.model.HttpWebHook;

import com.google.common.base.Strings;

/**
 * author Valeriy Mironichev
 */
public class HttpWebHookToJavaSerializer implements ToJavaSerializer<HttpWebHook> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpWebHook httpWebHook) {
        StringBuffer output = new StringBuffer();
        if (httpWebHook != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("callback()");
            if (httpWebHook.getHttpWebHookConfig() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output)
                        .append(".withHttpWebHookConfig(\"").append(httpWebHook.getHttpWebHookConfig()).append("\")");
            }
        }

        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(System.getProperty("line.separator"))
                .append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }

}
