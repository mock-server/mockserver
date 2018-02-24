package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.mock.Expectation;

import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class ExpectationToJavaSerializer implements ToJavaSerializer<Expectation> {

    public static final int INDENT_SIZE = 8;

    public String serialize(List<Expectation> expectations) {
        StringBuilder output = new StringBuilder();
        for (Expectation expectation : expectations) {
            output.append(serialize(0, expectation));
            output.append(NEW_LINE);
            output.append(NEW_LINE);
        }
        return output.toString();
    }

    @Override
    public String serialize(int numberOfSpacesToIndent, Expectation expectation) {
        StringBuffer output = new StringBuffer();
        if (expectation != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("new MockServerClient(\"localhost\", 1080)");
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".when(");
            output.append(new HttpRequestToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getHttpRequest()));
            if (expectation.getTimes() != null) {
                output.append(",").append(new TimesToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getTimes()));
            }
            if (expectation.getTimeToLive() != null) {
                output.append(",").append(new TimeToLiveToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getTimeToLive()));
            }
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            if (expectation.getHttpResponse() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".respond(");
                output.append(new HttpResponseToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getHttpResponse()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            if (expectation.getHttpResponseTemplate() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".respond(");
                output.append(new HttpTemplateToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getHttpResponseTemplate()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            if (expectation.getHttpResponseClassCallback() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".respond(");
                output.append(new HttpClassCallbackToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getHttpResponseClassCallback()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            if (expectation.getHttpResponseObjectCallback() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("/*NOT POSSIBLE TO GENERATE CODE FOR OBJECT CALLBACK*/");
            }
            if (expectation.getHttpForward() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".forward(");
                output.append(new HttpForwardToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getHttpForward()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            if (expectation.getHttpForwardTemplate() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".forward(");
                output.append(new HttpTemplateToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getHttpForwardTemplate()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            if (expectation.getHttpForwardClassCallback() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".forward(");
                output.append(new HttpClassCallbackToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getHttpForwardClassCallback()));
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            if (expectation.getHttpForwardObjectCallback() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("/*NOT POSSIBLE TO GENERATE CODE FOR OBJECT CALLBACK*/");
            }
            if (expectation.getHttpError() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".error(");
                output.append(new HttpErrorToJavaSerializer().serialize(numberOfSpacesToIndent + 1, expectation.getHttpError()));
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
