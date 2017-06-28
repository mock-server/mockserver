package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.mock.Expectation;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class ExpectationToJavaSerializer implements ToJavaSerializer<Expectation> {

    public static final int INDENT_SIZE = 8;

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, Expectation expectation) {
        StringBuffer output = new StringBuffer();
        if (expectation != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("new MockServerClient(\"localhost\", 1080)");
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".when(");
            output.append(new HttpRequestToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 1, expectation.getHttpRequest())).append(",");
            appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append("Times.once()");
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            if (expectation.getHttpResponse() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".respond(");
                output.append(new HttpResponseToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 1, expectation.getHttpResponse()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            if (expectation.getHttpForward() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".forward(");
                output.append(new HttpForwardToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 1, expectation.getHttpForward()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            if (expectation.getHttpError() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".error(");
                output.append(new HttpErrorToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 1, expectation.getHttpError()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            if (expectation.getHttpClassCallback() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".callback(");
                output.append(new HttpCallbackToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 1, expectation.getHttpClassCallback()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            output.append(";");
        }
        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
