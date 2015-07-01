package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.mock.Expectation;

/**
 * @author jamesdbloom
 */
public class ExpectationToJavaSerializer implements ToJavaSerializer<Expectation> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, Expectation expectation) {
        StringBuffer output = new StringBuffer();
        if (expectation != null) {
            int indentSize = 8;
            appendNewLineAndIndent(numberOfSpacesToIndent * indentSize, output).append("new MockServerClient(\"localhost\", 1080)");
            appendNewLineAndIndent(numberOfSpacesToIndent * indentSize, output).append(".when(");
            output.append(new HttpRequestToJavaSerializer().serializeAsJava((numberOfSpacesToIndent + 1) * indentSize, expectation.getHttpRequest())).append(",");
            appendNewLineAndIndent((numberOfSpacesToIndent + 1) * indentSize, output).append("Times.once()");
            appendNewLineAndIndent(numberOfSpacesToIndent * indentSize, output).append(")");
            if (expectation.getHttpResponse(false) != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * indentSize, output).append(".respond(");
                output.append(new HttpResponseToJavaSerializer().serializeAsJava((numberOfSpacesToIndent + 1) * indentSize, expectation.getHttpResponse(false)));
                appendNewLineAndIndent(numberOfSpacesToIndent * indentSize, output).append(")");
            }
            if (expectation.getHttpForward() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * indentSize, output).append(".forward(");
                output.append(new HttpForwardToJavaSerializer().serializeAsJava((numberOfSpacesToIndent + 1) * indentSize, expectation.getHttpForward()));
                appendNewLineAndIndent(numberOfSpacesToIndent * indentSize, output).append(")");
            }
            if (expectation.getHttpCallback() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * indentSize, output).append(".callback(");
                output.append(new HttpCallbackToJavaSerializer().serializeAsJava((numberOfSpacesToIndent + 1) * indentSize, expectation.getHttpCallback()));
                appendNewLineAndIndent(numberOfSpacesToIndent * indentSize, output).append(")");
            }
            output.append(";");
        }
        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(System.getProperty("line.separator")).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
