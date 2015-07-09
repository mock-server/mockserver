package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.model.HttpCallback;

import static org.mockserver.client.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpCallbackToJavaSerializer implements ToJavaSerializer<HttpCallback> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpCallback httpCallback) {
        StringBuffer output = new StringBuffer();
        if (httpCallback != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("callback()");
            if (httpCallback.getCallbackClass() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withCallbackClass(\"").append(httpCallback.getCallbackClass()).append("\")");
            }
        }

        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(System.getProperty("line.separator")).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
