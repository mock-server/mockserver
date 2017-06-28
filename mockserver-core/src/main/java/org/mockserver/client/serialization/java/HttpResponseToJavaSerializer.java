package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.apache.commons.lang3.StringEscapeUtils;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.*;

import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.client.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpResponseToJavaSerializer implements ToJavaSerializer<HttpResponse> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpResponse httpResponse) {
        StringBuffer output = new StringBuffer();
        if (httpResponse != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("response()");
            if (httpResponse.getStatusCode() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withStatusCode(").append(httpResponse.getStatusCode()).append(")");
            }
            outputHeaders(numberOfSpacesToIndent + 1, output, httpResponse.getHeaders());
            outputCookies(numberOfSpacesToIndent + 1, output, httpResponse.getCookies());
            if (httpResponse.getBodyAsString() != null && httpResponse.getBodyAsString().length() > 0) {
                if (httpResponse.getBody() instanceof BinaryBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    BinaryBody body = (BinaryBody) httpResponse.getBody();
                    output.append(".withBody(Base64Converter.base64StringToBytes(\"").append(Base64Converter.bytesToBase64String(body.getRawBytes())).append("\"))");
                } else {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withBody(\"").append(StringEscapeUtils.escapeJava(httpResponse.getBodyAsString())).append("\")");
                }
            }
            if (httpResponse.getDelay() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withDelay(").append(new DelayToJavaSerializer().serializeAsJava(0, httpResponse.getDelay())).append(")");
            }
            if (httpResponse.getConnectionOptions() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withConnectionOptions(");
                output.append(new ConnectionOptionsToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 2, httpResponse.getConnectionOptions()));
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(")");
            }
        }

        return output.toString();
    }

    private void outputCookies(int numberOfSpacesToIndent, StringBuffer output, List<Cookie> cookies) {
        if (cookies.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".withCookies(");
            appendObject(numberOfSpacesToIndent + 1, output, new CookieToJavaSerializer(), cookies);
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
        }
    }

    private void outputHeaders(int numberOfSpacesToIndent, StringBuffer output, List<Header> headers) {
        if (headers.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".withHeaders(");
            appendObject(numberOfSpacesToIndent + 1, output, new HeaderToJavaSerializer(), headers);
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
        }
    }

    private <T extends ObjectWithReflectiveEqualsHashCodeToString> StringBuffer appendObject(int numberOfSpacesToIndent, StringBuffer output, MultiValueToJavaSerializer<T> toJavaSerializer, List<T> objects) {
        return output.append(toJavaSerializer.serializeAsJava(numberOfSpacesToIndent, objects));
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
