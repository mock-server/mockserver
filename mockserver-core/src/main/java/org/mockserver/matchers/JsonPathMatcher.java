package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.lang3.StringUtils;
import net.minidev.json.JSONArray;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

/**
 * See https://github.com/json-path/JsonPath
 *
 * @author jamesdbloom
 */
public class JsonPathMatcher extends BodyMatcher<String> {
    private static final String[] excludedFields = {"mockServerLogger", "jsonPath"};
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private JsonPath jsonPath;

    public JsonPathMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        if (StringUtils.isNotBlank(matcher)) {
            try {
                jsonPath = JsonPath.compile(matcher);
            } catch (Throwable throwable) {
                mockServerLogger.trace("Error while creating xpath expression for [" + matcher + "] assuming matcher not xpath - " + throwable.getMessage(), throwable);
            }
        }
    }

    public boolean matches(final HttpRequest context, String matched) {
        boolean result = false;

        if (jsonPath == null) {
            mockServerLogger.trace(context, "Attempting match against null XPath Expression for [" + matched + "]" + new RuntimeException("Attempting match against null XPath Expression for [" + matched + "]"));
        } else if (matcher.equals(matched)) {
            result = true;
        } else if (matched != null) {
            try {
                result = !jsonPath.<JSONArray>read(matched).isEmpty();
            } catch (Exception e) {
                mockServerLogger.trace(context, "Failed to match JSON: {}with JsonPath: {}because: {}", matched, jsonPath, e.getMessage());
            }
        }

        if (!result) {
            mockServerLogger.trace("Failed to match [{}] with [{}]", matched, matcher);
        }

        return not != result;
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }

}
