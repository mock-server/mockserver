package org.mockserver.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.model.HttpClassCallback;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpClassCallbackToJavaSerializer implements ToJavaSerializer<HttpClassCallback> {

    @Override
    public String serialize(int numberOfSpacesToIndent, HttpClassCallback httpClassCallback) {
        StringBuffer output = new StringBuffer();
        if (httpClassCallback != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("callback()");
            if (httpClassCallback.getCallbackClass() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withCallbackClass(\"").append(httpClassCallback.getCallbackClass()).append("\")");
            }
            if (httpClassCallback.getDelay() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withDelay(").append(new DelayToJavaSerializer().serialize(0, httpClassCallback.getDelay())).append(")");
            }
        }

        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
