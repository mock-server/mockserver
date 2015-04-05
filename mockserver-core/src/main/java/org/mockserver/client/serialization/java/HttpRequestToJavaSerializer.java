package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.*;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequestToJavaSerializer implements ToJavaSerializer<HttpRequest> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpRequest httpRequest) {
        StringBuffer output = new StringBuffer();
        if (httpRequest != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent, output);
            output.append("request()");
            if (httpRequest.getMethod() != null && StringUtils.isNotEmpty(httpRequest.getMethod().getValue())) {
                appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                output.append(".withMethod(\"").append(httpRequest.getMethod().getValue()).append("\")");
            }
            if (httpRequest.getPath() != null && StringUtils.isNotEmpty(httpRequest.getPath().getValue())) {
                appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                output.append(".withPath(\"").append(httpRequest.getPath().getValue()).append("\")");
            }
            outputHeaders(numberOfSpacesToIndent + 8, output, httpRequest.getHeaders());
            outputCookies(numberOfSpacesToIndent + 8, output, httpRequest.getCookies());
            outputParameters(numberOfSpacesToIndent + 8, output, "QueryStringParameter", httpRequest.getQueryStringParameters());
            if (httpRequest.getBody() != null) {
                if (httpRequest.getBody() instanceof JsonBody) {
                    appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                    output.append(".withBody(");
                    JsonBody jsonBody = (JsonBody) httpRequest.getBody();
                    output.append("new JsonBody(\"").append(StringEscapeUtils.escapeJava(jsonBody.getValue())).append("\", JsonBodyMatchType.").append(jsonBody.getMatchType()).append(")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof JsonSchemaBody) {
                    appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                    output.append(".withBody(");
                    JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) httpRequest.getBody();
                    output.append("new JsonSchemaBody(\"").append(StringEscapeUtils.escapeJava(jsonSchemaBody.getValue())).append("\")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof XPathBody) {
                    appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                    output.append(".withBody(");
                    XPathBody xPathBody = (XPathBody) httpRequest.getBody();
                    output.append("new XPathBody(\"").append(StringEscapeUtils.escapeJava(xPathBody.getValue())).append("\")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof RegexBody) {
                    appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                    output.append(".withBody(");
                    RegexBody regexBody = (RegexBody) httpRequest.getBody();
                    output.append("new RegexBody(\"").append(StringEscapeUtils.escapeJava(regexBody.getValue())).append("\")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof StringBody) {
                    appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                    output.append(".withBody(");
                    StringBody stringBody = (StringBody) httpRequest.getBody();
                    output.append("new StringBody(\"").append(StringEscapeUtils.escapeJava(stringBody.getValue())).append("\")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof ParameterBody) {
                    appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                    output.append(".withBody(");
                    appendNewLineAndIndent(numberOfSpacesToIndent + 16, output);
                    output.append("new ParameterBody(");
                    List<Parameter> bodyParameters = ((ParameterBody) httpRequest.getBody()).getValue();
                    output.append(new ParameterToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 24, bodyParameters));
                    appendNewLineAndIndent(numberOfSpacesToIndent + 16, output);
                    output.append(")");
                    appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                    output.append(")");
                } else if (httpRequest.getBody() instanceof BinaryBody) {
                    appendNewLineAndIndent(numberOfSpacesToIndent + 8, output);
                    output.append(".withBody(new byte[0]) /* note: not possible to generate code for binary data */");
                }
            }
        }

        return output.toString();
    }

    private void outputParameters(int numberOfSpacesToIndent, StringBuffer output, String fieldName, List<Parameter> parameters) {
        if (parameters.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent, output).append(".with").append(fieldName).append("s(");
            appendObject(numberOfSpacesToIndent, output, new ParameterToJavaSerializer(), parameters);
            appendNewLineAndIndent(numberOfSpacesToIndent, output).append(")");
        }
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
