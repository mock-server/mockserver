package org.mockserver.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.model.SocketAddress;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class SocketAddressToJavaSerializer implements ToJavaSerializer<SocketAddress> {

    @Override
    public String serialize(int numberOfSpacesToIndent, SocketAddress socketAddress) {
        StringBuffer output = new StringBuffer();
        if (socketAddress != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("new SocketAddress()");
            if (socketAddress.getHost() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withHost(\"").append(socketAddress.getHost()).append("\")");
            }
            if (socketAddress.getPort() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withPort(").append(socketAddress.getPort()).append(")");
            }
            if (socketAddress.getScheme() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withScheme(SocketAddress.Scheme.").append(socketAddress.getScheme()).append(")");
            }
        }
        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
