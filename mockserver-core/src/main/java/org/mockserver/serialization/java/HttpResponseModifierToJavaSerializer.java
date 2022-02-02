package org.mockserver.serialization.java;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.mockserver.model.Cookies;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpResponseModifier;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.serialization.Base64Converter;

import java.util.List;
import java.util.stream.Collectors;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpResponseModifierToJavaSerializer implements ToJavaSerializer<HttpResponseModifier> {

    private final Base64Converter base64Converter = new Base64Converter();

    public String serialize(List<HttpResponseModifier> httpResponseModifiers) {
        StringBuilder output = new StringBuilder();
        for (HttpResponseModifier httpResponseModifier : httpResponseModifiers) {
            output.append(serialize(0, httpResponseModifier));
            output.append(";");
            output.append(NEW_LINE);
        }
        return output.toString();
    }

    @Override
    public String serialize(int numberOfSpacesToIndent, HttpResponseModifier response) {
        StringBuffer output = new StringBuffer();
        if (response != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output);
            output.append("responseModifier()");
            if (response.getHeaders() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withHeaders(");
                outputHeaders(numberOfSpacesToIndent, output, response.getHeaders().getAdd());
                outputHeaders(numberOfSpacesToIndent, output, response.getHeaders().getReplace());
                outputList(numberOfSpacesToIndent, output, response.getHeaders().getRemove());
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(")");
            }
            if (response.getCookies() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withCookies(");
                outputCookies(numberOfSpacesToIndent, output, response.getCookies().getAdd());
                outputCookies(numberOfSpacesToIndent, output, response.getCookies().getReplace());
                outputList(numberOfSpacesToIndent, output, response.getCookies().getRemove());
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(")");
            }
        }

        return output.toString();
    }

    private void outputHeaders(int numberOfSpacesToIndent, StringBuffer output, Headers headers) {
        if (headers != null && !headers.isEmpty()) {
            appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output).append("headers(");
            appendObject((numberOfSpacesToIndent + 2), output, new HeaderToJavaSerializer(), headers.getEntries());
            appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output).append("),");
        } else {
            appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output).append("null,");
        }
    }

    private void outputCookies(int numberOfSpacesToIndent, StringBuffer output, Cookies cookies) {
        if (cookies != null && !cookies.isEmpty()) {
            appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output).append("cookies(");
            appendObject((numberOfSpacesToIndent + 2), output, new CookieToJavaSerializer(), cookies.getEntries());
            appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output).append("),");
        } else {
            appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output).append("null,");
        }
    }

    private void outputList(int numberOfSpacesToIndent, StringBuffer output, List<String> add) {
        if (add != null && !add.isEmpty()) {
            appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output).append("ImmutableList.of(").append(Joiner.on(",").join(add.stream().map(s -> "\"" + s + "\"").collect(Collectors.toList()))).append(")");
        } else {
            appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output).append("null");
        }
    }

    private <T extends ObjectWithReflectiveEqualsHashCodeToString> void appendObject(int numberOfSpacesToIndent, StringBuffer output, MultiValueToJavaSerializer<T> toJavaSerializer, List<T> objects) {
        output.append(toJavaSerializer.serializeAsJava(numberOfSpacesToIndent + 1, objects));
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
