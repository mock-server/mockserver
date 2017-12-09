package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.apache.commons.text.StringEscapeUtils;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.*;

import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.client.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpRequestToJavaSerializer implements ToJavaSerializer<HttpRequest> {

    private final Base64Converter base64Converter = new Base64Converter();

    public String serialize(List<HttpRequest> httpRequests) {
        StringBuilder output = new StringBuilder();
        for (HttpRequest httpRequest : httpRequests) {
            output.append(serialize(0, httpRequest));
            output.append(";");
            output.append(NEW_LINE);
        }
        return output.toString();
    }

    @Override
    public String serialize(int numberOfSpacesToIndent, HttpRequest request) {
        StringBuffer output = new StringBuffer();
        if (request != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output);
            output.append("request()");
            if (!Strings.isNullOrEmpty(request.getMethod().getValue())) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withMethod(\"").append(request.getMethod().getValue()).append("\")");
            }
            if (!Strings.isNullOrEmpty(request.getPath().getValue())) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withPath(\"").append(request.getPath().getValue()).append("\")");
            }
            outputHeaders(numberOfSpacesToIndent + 1, output, request.getHeaderList());
            outputCookies(numberOfSpacesToIndent + 1, output, request.getCookieList());
            outputQueryStringParameter(numberOfSpacesToIndent + 1, output, request.getQueryStringParameterList());
            if (request.isSecure() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withSecure(").append(request.isSecure().toString()).append(")");
            }
            if (request.isKeepAlive() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withKeepAlive(").append(request.isKeepAlive().toString()).append(")");
            }
            if (request.getBody() != null) {
                if (request.getBody() instanceof JsonBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    JsonBody jsonBody = (JsonBody) request.getBody();
                    output.append("new JsonBody(\"").append(StringEscapeUtils.escapeJava(jsonBody.getValue())).append("\", JsonBodyMatchType.").append(jsonBody.getMatchType()).append(")");
                    output.append(")");
                } else if (request.getBody() instanceof JsonSchemaBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) request.getBody();
                    output.append("new JsonSchemaBody(\"").append(StringEscapeUtils.escapeJava(jsonSchemaBody.getValue())).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof XPathBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    XPathBody xPathBody = (XPathBody) request.getBody();
                    output.append("new XPathBody(\"").append(StringEscapeUtils.escapeJava(xPathBody.getValue())).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof XmlSchemaBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    XmlSchemaBody xmlSchemaBody = (XmlSchemaBody) request.getBody();
                    output.append("new XmlSchemaBody(\"").append(StringEscapeUtils.escapeJava(xmlSchemaBody.getValue())).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof RegexBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    RegexBody regexBody = (RegexBody) request.getBody();
                    output.append("new RegexBody(\"").append(StringEscapeUtils.escapeJava(regexBody.getValue())).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof StringBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    StringBody stringBody = (StringBody) request.getBody();
                    output.append("new StringBody(\"").append(StringEscapeUtils.escapeJava(stringBody.getValue())).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof ParameterBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output);
                    output.append("new ParameterBody(");
                    List<Parameter> bodyParameters = ((ParameterBody) request.getBody()).getValue();
                    output.append(new ParameterToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 3, bodyParameters));
                    appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output);
                    output.append(")");
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(")");
                } else if (request.getBody() instanceof BinaryBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    BinaryBody body = (BinaryBody) request.getBody();
                    output.append(".withBody(new Base64Converter().base64StringToBytes(\"").append(base64Converter.bytesToBase64String(body.getRawBytes())).append("\"))");
                }
            }
        }

        return output.toString();
    }

    private void outputQueryStringParameter(int numberOfSpacesToIndent, StringBuffer output, List<Parameter> parameters) {
        if (parameters.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".withQueryStringParameters(");
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

    private <T extends ObjectWithReflectiveEqualsHashCodeToString> void appendObject(int numberOfSpacesToIndent, StringBuffer output, MultiValueToJavaSerializer<T> toJavaSerializer, List<T> objects) {
        output.append(toJavaSerializer.serializeAsJava(numberOfSpacesToIndent + 1, objects));
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
