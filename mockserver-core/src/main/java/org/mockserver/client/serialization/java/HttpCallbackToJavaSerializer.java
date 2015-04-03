package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.model.HttpCallback;

/**
 * @author jamesdbloom
 */
public class HttpCallbackToJavaSerializer implements ToJavaSerializer<HttpCallback> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpCallback httpCallback) {
        StringBuffer output = new StringBuffer();
        if (httpCallback != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent, output).append("callback()");
            if (httpCallback.getCallbackClass() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent + 8, output).append(".withCallbackClass(\"").append(httpCallback.getCallbackClass()).append("\")");
            }
        }

        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(System.getProperty("line.separator")).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
