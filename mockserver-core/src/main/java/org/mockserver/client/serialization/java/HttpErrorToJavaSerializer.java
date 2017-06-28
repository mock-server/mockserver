package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.HttpError;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.client.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpErrorToJavaSerializer implements ToJavaSerializer<HttpError> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpError httpError) {
        StringBuffer output = new StringBuffer();
        if (httpError != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("error()");
            if (httpError.getDelay() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withDelay(").append(new DelayToJavaSerializer().serializeAsJava(0, httpError.getDelay())).append(")");
            }
            if (httpError.getDropConnection() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withDropConnection(").append(httpError.getDropConnection()).append(")");
            }
            if (httpError.getResponseBytes() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withResponseBytes(Base64Converter.base64StringToBytes(\"").append(Base64Converter.bytesToBase64String(httpError.getResponseBytes())).append("\"))");
            }
        }
        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
