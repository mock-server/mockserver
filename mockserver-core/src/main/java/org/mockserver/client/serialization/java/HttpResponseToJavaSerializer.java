package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.apache.commons.lang3.StringEscapeUtils;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpResponseToJavaSerializer implements ToJavaSerializer<HttpResponse> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpResponse httpResponse) {
        StringBuffer output = new StringBuffer();
        if (httpResponse != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent, output).append("response()");
            if (httpResponse.getStatusCode() != null) {
                appendNewLineAndIndent(numberOfSpacesToIndent + 8, output).append(".withStatusCode(").append(httpResponse.getStatusCode()).append(")");
            }
            outputHeaders(numberOfSpacesToIndent + 8, output, httpResponse.getHeaders());
            outputCookies(numberOfSpacesToIndent + 8, output, httpResponse.getCookies());
            if (httpResponse.getBodyAsString() != null && httpResponse.getBodyAsString().length() > 0) {
                appendNewLineAndIndent(numberOfSpacesToIndent + 8, output).append(".withBody(\"").append(StringEscapeUtils.escapeJava(httpResponse.getBodyAsString())).append("\")");
            }
        }

        return output.toString();
    }

    private void outputCookies(int numberOfSpacesToIndent, StringBuffer output, List<Cookie> cookies) {
        if (cookies.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent, output).append(".withCookies(");
            appendObject(numberOfSpacesToIndent, output, new CookieToJavaSerializer(), cookies);
            appendNewLineAndIndent(numberOfSpacesToIndent, output).append(")");
        }
    }

    private void outputHeaders(int numberOfSpacesToIndent, StringBuffer output, List<Header> headers) {
        if (headers.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent, output).append(".withHeaders(");
            appendObject(numberOfSpacesToIndent, output, new HeaderToJavaSerializer(), headers);
            appendNewLineAndIndent(numberOfSpacesToIndent, output).append(")");
        }
    }

    private <T extends ObjectWithReflectiveEqualsHashCodeToString> StringBuffer appendObject(int numberOfSpacesToIndent, StringBuffer output, MultiValueToJavaSerializer<T> toJavaSerializer, List<T> objects) {
        return output.append(toJavaSerializer.serializeAsJava(numberOfSpacesToIndent + 8, objects));
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(System.getProperty("line.separator")).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
