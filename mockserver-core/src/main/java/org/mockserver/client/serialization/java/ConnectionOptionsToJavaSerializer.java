package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpError;

import static org.mockserver.client.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class ConnectionOptionsToJavaSerializer implements ToJavaSerializer<ConnectionOptions> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, ConnectionOptions connectionOptions) {
        StringBuffer output = new StringBuffer();
        if (connectionOptions != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("connectionOptions()");
            if (connectionOptions.getSuppressContentLengthHeader() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withSuppressContentLengthHeader(").append(connectionOptions.getSuppressContentLengthHeader()).append(")");
            }
            if (connectionOptions.getContentLengthHeaderOverride() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withContentLengthHeaderOverride(").append(connectionOptions.getContentLengthHeaderOverride()).append(")");
            }
            if (connectionOptions.getSuppressConnectionHeader() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withSuppressConnectionHeader(").append(connectionOptions.getSuppressConnectionHeader()).append(")");
            }
            if (connectionOptions.getKeepAliveOverride() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withKeepAliveOverride(").append(connectionOptions.getKeepAliveOverride()).append(")");
            }
            if (connectionOptions.getCloseSocket() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withCloseSocket(").append(connectionOptions.getCloseSocket()).append(")");
            }
        }
        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(System.getProperty("line.separator")).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
