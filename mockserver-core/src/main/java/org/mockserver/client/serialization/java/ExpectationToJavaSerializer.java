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
            appendNewLineAndIndent(8, output).append("new MockServerClient(\"localhost\", 1080)");
            appendNewLineAndIndent(8, output).append(".when(");
            output.append(new HttpRequestToJavaSerializer().serializeAsJava(16, expectation.getHttpRequest())).append(",");
            appendNewLineAndIndent(16, output).append("Times.once()");
            appendNewLineAndIndent(8, output).append(")");
            if (expectation.getHttpResponse(false) != null) {
                appendNewLineAndIndent(8, output).append(".respond(");
                output.append(new HttpResponseToJavaSerializer().serializeAsJava(16, expectation.getHttpResponse(false)));
                appendNewLineAndIndent(8, output).append(")");
            }
            if (expectation.getHttpForward() != null) {
                appendNewLineAndIndent(8, output).append(".forward(");
                output.append(new HttpForwardToJavaSerializer().serializeAsJava(16, expectation.getHttpForward()));
                appendNewLineAndIndent(8, output).append(")");
            }
            if (expectation.getHttpCallback() != null) {
                appendNewLineAndIndent(8, output).append(".callback(");
                output.append(new HttpCallbackToJavaSerializer().serializeAsJava(16, expectation.getHttpCallback()));
                appendNewLineAndIndent(8, output).append(")");
            }
            output.append(";");
        }
        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(System.getProperty("line.separator")).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
