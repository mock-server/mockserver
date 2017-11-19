package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.*;

import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.client.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpRequestToJavaSerializer implements ToJavaSerializer<HttpRequest> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpRequest httpRequest) {
        StringBuffer output = new StringBuffer();
        if (httpRequest != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output);
            output.append("request()");
            if (httpRequest.getMethod() != null && StringUtils.isNotEmpty(httpRequest.getMethod().getValue())) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withMethod(\"").append(httpRequest.getMethod().getValue()).append("\")");
            }
            if (httpRequest.getPath() != null && StringUtils.isNotEmpty(httpRequest.getPath().getValue())) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withPath(\"").append(httpRequest.getPath().getValue()).append("\")");
            }
            outputHeaders(numberOfSpacesToIndent + 1, output, httpRequest.getHeaders());
            outputCookies(numberOfSpacesToIndent + 1, output, httpRequest.getCookies());
            outputParameters(numberOfSpacesToIndent + 1, output, "QueryStringParameter", httpRequest.getQueryStringParameters());
            if (httpRequest.isSecure() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withSecure(").append(httpRequest.isSecure().toString()).append(")");
            }
            if (httpRequest.isKeepAlive() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withKeepAlive(").append(httpRequest.isKeepAlive().toString()).append(")");
            }
            if (httpRequest.getBody() != null) {
                if (httpRequest.getBody() instanceof JsonBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    JsonBody jsonBody = (JsonBody) httpRequest.getBody();
                    output.append("new JsonBody(\"").append(StringEscapeUtils.escapeJava(jsonBody.getValue())).append("\", JsonBodyMatchType.").append(jsonBody.getMatchType()).append(")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof JsonSchemaBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) httpRequest.getBody();
                    output.append("new JsonSchemaBody(\"").append(StringEscapeUtils.escapeJava(jsonSchemaBody.getValue())).append("\")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof XPathBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    XPathBody xPathBody = (XPathBody) httpRequest.getBody();
                    output.append("new XPathBody(\"").append(StringEscapeUtils.escapeJava(xPathBody.getValue())).append("\")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof XmlSchemaBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    XmlSchemaBody xmlSchemaBody = (XmlSchemaBody) httpRequest.getBody();
                    output.append("new XmlSchemaBody(\"").append(StringEscapeUtils.escapeJava(xmlSchemaBody.getValue())).append("\")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof RegexBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    RegexBody regexBody = (RegexBody) httpRequest.getBody();
                    output.append("new RegexBody(\"").append(StringEscapeUtils.escapeJava(regexBody.getValue())).append("\")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof StringBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    StringBody stringBody = (StringBody) httpRequest.getBody();
                    output.append("new StringBody(\"").append(StringEscapeUtils.escapeJava(stringBody.getValue())).append("\")");
                    output.append(")");
                } else if (httpRequest.getBody() instanceof ParameterBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output);
                    output.append("new ParameterBody(");
                    List<Parameter> bodyParameters = ((ParameterBody) httpRequest.getBody()).getValue();
                    output.append(new ParameterToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 3, bodyParameters));
                    appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output);
                    output.append(")");
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(")");
                } else if (httpRequest.getBody() instanceof BinaryBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    BinaryBody body = (BinaryBody) httpRequest.getBody();
                    output.append(".withBody(Base64Converter.base64StringToBytes(\"").append(Base64Converter.bytesToBase64String(body.getRawBytes())).append("\"))");
                }
            }
        }

        return output.toString();
    }

    private void outputParameters(int numberOfSpacesToIndent, StringBuffer output, String fieldName, List<Parameter> parameters) {
        if (parameters.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".with").append(fieldName).append("s(");
            appendObject(numberOfSpacesToIndent, output, new ParameterToJavaSerializer(), parameters);
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
        }
    }

    private void outputCookies(int numberOfSpacesToIndent, StringBuffer output, List<Cookie> cookies) {
        if (cookies.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".withCookies(");
            appendObject(numberOfSpacesToIndent, output, new CookieToJavaSerializer(), cookies);
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
        }
    }

    private void outputHeaders(int numberOfSpacesToIndent, StringBuffer output, List<Header> headers) {
        if (headers.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".withHeaders(");
            appendObject(numberOfSpacesToIndent, output, new HeaderToJavaSerializer(), headers);
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
        }
    }

    private <T extends ObjectWithReflectiveEqualsHashCodeToString> StringBuffer appendObject(int numberOfSpacesToIndent, StringBuffer output, MultiValueToJavaSerializer<T> toJavaSerializer, List<T> objects) {
        return output.append(toJavaSerializer.serializeAsJava(numberOfSpacesToIndent + 1, objects));
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
