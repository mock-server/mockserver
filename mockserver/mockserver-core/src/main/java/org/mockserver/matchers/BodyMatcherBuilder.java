package org.mockserver.matchers;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.FileBodyMaterialiser;
import org.mockserver.model.*;

import static org.mockserver.matchers.NotMatcher.notMatcher;

/**
 * Builds a {@link BodyMatcher} for a given {@link Body} template.
 * <p>
 * Extracted from {@link HttpRequestPropertiesMatcher} so the same body-matching
 * logic can be reused for response verification without duplication.
 */
public class BodyMatcherBuilder {

    public static BodyMatcher buildBodyMatcher(Configuration configuration, MockServerLogger mockServerLogger, Body body, boolean controlPlaneMatcher) {
        BodyMatcher bodyMatcher = null;
        // Exact-case (case-sensitive) string-body matching is opt-in via matchExactCase and only
        // applies to data-plane request matching; control-plane (clear/verify/retrieve) body filters
        // keep the historical case-insensitive behaviour. ExactStringMatcher and SubStringMatcher are
        // already case-sensitive in their primary (non-substring) comparison, so this flag only changes
        // the regex body matcher's regex compilation and its equalsIgnoreCase fallback.
        boolean caseSensitive = !controlPlaneMatcher && configuration != null && configuration.matchExactCase();
        if (body != null) {
            switch (body.getType()) {
                case STRING:
                    StringBody stringBody = (StringBody) body;
                    if (stringBody.isSubString()) {
                        bodyMatcher = new SubStringMatcher(mockServerLogger, NottableString.string(stringBody.getValue()));
                    } else {
                        bodyMatcher = new ExactStringMatcher(mockServerLogger, NottableString.string(stringBody.getValue()));
                    }
                    break;
                case REGEX:
                    RegexBody regexBody = (RegexBody) body;
                    bodyMatcher = new RegexStringMatcher(mockServerLogger, NottableString.string(regexBody.getValue()), controlPlaneMatcher, caseSensitive);
                    break;
                case FUZZY:
                    FuzzyBody fuzzyBody = (FuzzyBody) body;
                    bodyMatcher = new FuzzyMatcher(mockServerLogger, fuzzyBody.getValue(), fuzzyBody.getThreshold(), fuzzyBody.isIgnoreCase());
                    break;
                case PARAMETERS:
                    ParameterBody parameterBody = (ParameterBody) body;
                    bodyMatcher = new ParameterStringMatcher(configuration, mockServerLogger, parameterBody.getValue(), controlPlaneMatcher);
                    break;
                case MULTIPART:
                    MultipartBody multipartBody = (MultipartBody) body;
                    bodyMatcher = new MultipartMatcher(mockServerLogger, multipartBody, controlPlaneMatcher);
                    break;
                case XPATH:
                    XPathBody xPathBody = (XPathBody) body;
                    bodyMatcher = new XPathMatcher(mockServerLogger, xPathBody.getValue(), xPathBody.getNamespacePrefixes(), xPathBody.getDateTime());
                    break;
                case XML:
                    XmlBody xmlBody = (XmlBody) body;
                    bodyMatcher = new XmlStringMatcher(mockServerLogger, xmlBody.getValue());
                    break;
                case JSON:
                    JsonBody jsonBody = (JsonBody) body;
                    bodyMatcher = new JsonStringMatcher(mockServerLogger, jsonBody.getValue(), jsonBody.getMatchType(), jsonBody.isMatchNumbersAsStrings());
                    break;
                case JSON_SCHEMA:
                    JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) body;
                    bodyMatcher = new JsonSchemaMatcher(mockServerLogger, jsonSchemaBody.getValue()).withParameterStyle(jsonSchemaBody.getParameterStyles());
                    break;
                case JSON_PATH:
                    JsonPathBody jsonPathBody = (JsonPathBody) body;
                    bodyMatcher = new JsonPathMatcher(mockServerLogger, jsonPathBody.getValue(), jsonPathBody.getDateTime());
                    break;
                case XML_SCHEMA:
                    XmlSchemaBody xmlSchemaBody = (XmlSchemaBody) body;
                    bodyMatcher = new XmlSchemaMatcher(mockServerLogger, xmlSchemaBody.getValue(), xmlSchemaBody.getSourceUri());
                    break;
                case JSON_RPC:
                    JsonRpcBody jsonRpcBody = (JsonRpcBody) body;
                    bodyMatcher = new JsonRpcMatcher(mockServerLogger, jsonRpcBody.getMethod(), jsonRpcBody.getParamsSchema());
                    break;
                case GRAPHQL:
                    GraphQLBody graphQLBody = (GraphQLBody) body;
                    bodyMatcher = new GraphQLMatcher(mockServerLogger, graphQLBody);
                    break;
                case BINARY:
                    BinaryBody binaryBody = (BinaryBody) body;
                    bodyMatcher = new BinaryMatcher(mockServerLogger, binaryBody.getValue());
                    break;
                case FILE:
                    // A FILE request-body matcher matches the request body against the EXACT contents of the
                    // referenced file (previously this was silently ignored - no case here left bodyMatcher
                    // null, so the body constraint matched anything). The file is read verbatim (never
                    // template-rendered - matching against a rendered template is not meaningful) via the
                    // shared FileBodyMaterialiser, then matched with the same semantics as an equivalent
                    // StringBody (exact, case-sensitive) or BinaryBody request body: a text content type
                    // yields an exact-string match; a binary or absent content type yields an exact-bytes match.
                    FileBody fileBody = (FileBody) body;
                    BodyWithContentType materialisedFileBody = new FileBodyMaterialiser(mockServerLogger, configuration).materialise(fileBody, null);
                    if (materialisedFileBody instanceof BinaryBody) {
                        bodyMatcher = new BinaryMatcher(mockServerLogger, ((BinaryBody) materialisedFileBody).getValue());
                    } else {
                        bodyMatcher = new ExactStringMatcher(mockServerLogger, NottableString.string(((StringBody) materialisedFileBody).getValue()));
                    }
                    break;
                case WASM:
                    WasmBody wasmBody = (WasmBody) body;
                    bodyMatcher = new WasmBodyMatcher(wasmBody.getModuleName(), configuration);
                    break;
                case ALL_OF:
                    AllOfBody allOfBody = (AllOfBody) body;
                    java.util.List<BodyMatcher> childMatchers = new java.util.ArrayList<>();
                    if (allOfBody.getValue() != null) {
                        for (Body child : allOfBody.getValue()) {
                            BodyMatcher childMatcher = buildBodyMatcher(configuration, mockServerLogger, child, controlPlaneMatcher);
                            if (childMatcher != null) {
                                childMatchers.add(childMatcher);
                            }
                        }
                    }
                    bodyMatcher = new AllOfBodyMatcher(childMatchers);
                    break;
            }
            if (body.isNot()) {
                //noinspection ConstantConditions
                bodyMatcher = notMatcher(bodyMatcher);
            }
        }
        return bodyMatcher;
    }

}
