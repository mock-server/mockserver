package org.mockserver.serialization.java;

import org.mockserver.model.*;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpRequestToJavaSerializer implements ToJavaSerializer<HttpRequest> {

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
        return JavaCode.toString(output -> write(numberOfSpacesToIndent, request, output));
    }

    /**
     * As {@link #serialize(int, HttpRequest)}, writing the code to {@code output}, the body as it is escaped.
     */
    void write(int numberOfSpacesToIndent, HttpRequest request, JavaCode output) {
        if (request != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output);
            output.append("request()");
            if (isNotBlank(request.getMethod().getValue())) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withMethod(\"").appendEscaped(request.getMethod().getValue()).append("\")");
            }
            if (isNotBlank(request.getPath().getValue())) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withPath(\"").appendEscaped(request.getPath().getValue()).append("\")");
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
            if (request.getProtocol() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withProtocol(Protocol.").append(request.getProtocol().toString()).append(")");
            }
            if (request.getClientIp() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                // an IP address never starts with '!', so the negation marker in the string form is unambiguous
                output.append(".withClientIp(\"").appendEscaped((request.getClientIp().isNot() ? "!" : "") + request.getClientIp().getValue()).append("\")");
            }
            if (request.getSocketAddress() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(".withSocketAddress(");
                output.append(new SocketAddressToJavaSerializer().serialize(numberOfSpacesToIndent + 2, request.getSocketAddress()));
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                output.append(")");
            }
            if (request.getBody() != null) {
                if (request.getBody() instanceof JsonBody jsonBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    if (jsonBody.isMatchNumbersAsStrings()) {
                        output.append("JsonBody.json(\"").appendEscaped(jsonBody.getValue()).append("\", JsonBodyMatchType.").append(jsonBody.getMatchType()).append(", true)");
                    } else {
                        output.append("new JsonBody(\"").appendEscaped(jsonBody.getValue()).append("\", JsonBodyMatchType.").append(jsonBody.getMatchType()).append(")");
                    }
                    output.append(")");
                } else if (request.getBody() instanceof JsonPathBody jsonPathBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    output.append("new JsonPathBody(\"").appendEscaped(jsonPathBody.getValue()).append("\"");
                    if (jsonPathBody.getDateTime() != null) {
                        output.append(", ").append(NottableStringToJavaSerializer.serialize(jsonPathBody.getDateTime(), false));
                    }
                    output.append(")");
                    output.append(")");
                } else if (request.getBody() instanceof JsonSchemaBody jsonSchemaBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    output.append("new JsonSchemaBody(\"").appendEscaped(jsonSchemaBody.getValue()).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof XmlBody xmlBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    output.append("new XmlBody(\"").appendEscaped(xmlBody.getValue()).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof XPathBody xPathBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    output.append("new XPathBody(\"").appendEscaped(xPathBody.getValue()).append("\"");
                    Map<String, String> namespacePrefixes = xPathBody.getNamespacePrefixes();
                    if (namespacePrefixes != null && !namespacePrefixes.isEmpty()) {
                        output.append(", java.util.Map.ofEntries(");
                        boolean first = true;
                        for (Map.Entry<String, String> prefix : namespacePrefixes.entrySet()) {
                            output.append(first ? "" : ", ").append("java.util.Map.entry(\"").appendEscaped(prefix.getKey()).append("\", \"").appendEscaped(prefix.getValue()).append("\")");
                            first = false;
                        }
                        output.append(")");
                    } else if (xPathBody.getDateTime() != null) {
                        output.append(", null");
                    }
                    if (xPathBody.getDateTime() != null) {
                        output.append(", ").append(NottableStringToJavaSerializer.serialize(xPathBody.getDateTime(), false));
                    }
                    output.append(")");
                    output.append(")");
                } else if (request.getBody() instanceof XmlSchemaBody xmlSchemaBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    output.append("new XmlSchemaBody(\"").appendEscaped(xmlSchemaBody.getValue()).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof RegexBody regexBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    output.append("new RegexBody(\"").appendEscaped(regexBody.getValue()).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof FuzzyBody fuzzyBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    output.append("new FuzzyBody(\"").appendEscaped(fuzzyBody.getValue()).append("\", ").append(fuzzyBody.getThreshold()).append("d, ").append(fuzzyBody.isIgnoreCase()).append(")");
                    output.append(")");
                } else if (request.getBody() instanceof StringBody stringBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    output.append("new StringBody(\"").appendEscaped(stringBody.getValue()).append("\")");
                    output.append(")");
                } else if (request.getBody() instanceof ParameterBody parameterBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(");
                    appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output);
                    output.append("new ParameterBody(");
                    List<Parameter> bodyParameters = parameterBody.getValue().getEntries();
                    output.append(new ParameterToJavaSerializer().serializeAsJava(numberOfSpacesToIndent + 3, bodyParameters));
                    appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output);
                    output.append(")");
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(")");
                } else if (request.getBody() instanceof GraphQLBody graphQLBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(GraphQLBody.graphQL(").append(FluentJavaBuilder.literal(graphQLBody.getQuery()));
                    if (graphQLBody.getVariablesSchema() != null) {
                        output.append(", ").append(FluentJavaBuilder.literal(graphQLBody.getOperationName()));
                        output.append(", ").append(FluentJavaBuilder.literal(graphQLBody.getVariablesSchema()));
                    } else if (graphQLBody.getOperationName() != null) {
                        output.append(", ").append(FluentJavaBuilder.literal(graphQLBody.getOperationName()));
                    }
                    output.append(")");
                    if (graphQLBody.getSelectionSetMatchType() != null) {
                        appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output);
                        output.append(".withSelectionSetMatchType(SelectionSetMatchType.").append(graphQLBody.getSelectionSetMatchType().name()).append(")");
                    }
                    if (graphQLBody.getFields() != null && !graphQLBody.getFields().isEmpty()) {
                        appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output);
                        output.append(".withFields(");
                        for (int fi = 0; fi < graphQLBody.getFields().size(); fi++) {
                            if (fi > 0) {
                                output.append(", ");
                            }
                            output.append("\"").appendEscaped(graphQLBody.getFields().get(fi)).append("\"");
                        }
                        output.append(")");
                    }
                    if (graphQLBody.getSchema() != null) {
                        appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output);
                        output.append(".withSchema(").append(FluentJavaBuilder.literal(graphQLBody.getSchema())).append(")");
                    }
                    output.append(")");
                } else if (request.getBody() instanceof BinaryBody binaryBody) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output);
                    output.append(".withBody(new Base64Converter().base64StringToBytes(\"").appendBase64(binaryBody.getRawBytes()).append("\"))");
                }
            }
        }
    }

    private void outputQueryStringParameter(int numberOfSpacesToIndent, JavaCode output, List<Parameter> parameters) {
        if (parameters.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".withQueryStringParameters(");
            appendObject(numberOfSpacesToIndent, output, new ParameterToJavaSerializer(), parameters);
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
        }
    }

    private void outputCookies(int numberOfSpacesToIndent, JavaCode output, List<Cookie> cookies) {
        if (cookies.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".withCookies(");
            appendObject(numberOfSpacesToIndent, output, new CookieToJavaSerializer(), cookies);
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
        }
    }

    private void outputHeaders(int numberOfSpacesToIndent, JavaCode output, List<Header> headers) {
        if (headers.size() > 0) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".withHeaders(");
            appendObject(numberOfSpacesToIndent, output, new HeaderToJavaSerializer(), headers);
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
        }
    }

    private <T extends ObjectWithReflectiveEqualsHashCodeToString> void appendObject(int numberOfSpacesToIndent, JavaCode output, MultiValueToJavaSerializer<T> toJavaSerializer, List<T> objects) {
        output.append(toJavaSerializer.serializeAsJava(numberOfSpacesToIndent + 1, objects));
    }

    private JavaCode appendNewLineAndIndent(int numberOfSpacesToIndent, JavaCode output) {
        return output.newLineAndIndent(numberOfSpacesToIndent);
    }
}
