package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.Parameter;
import org.mockserver.model.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class ParameterStringMatcher extends BodyMatcher<String> {
    private static final String[] excludedFields = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final MultiValueMapMatcher matcher;

    public ParameterStringMatcher(MockServerLogger mockServerLogger, Parameters parameters) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = new MultiValueMapMatcher(mockServerLogger, ((parameters != null ? parameters.toCaseInsensitiveRegexMultiMap() : new CaseInsensitiveRegexMultiMap())));
    }

    public boolean matches(HttpRequest context, String matched) {
        boolean result = false;

        if (matcher.matches(null, parseString(matched))) {
            result = true;
        }

        if (!result) {
            mockServerLogger.trace(context, "Failed to match [{}] with [{}]", matched, this.matcher);
        }

        return reverseResultIfNot(result);
    }

    private List<KeyToMultiValue> parseString(String matched) {
        Map<String, Parameter> mappedParameters = new HashMap<>();
        Map<String, List<String>> parameters = new QueryStringDecoder("?" + matched).parameters();
        for (String name : parameters.keySet()) {
            // TODO(jamesdbloom) support nottable parameters
            for (String value : parameters.get(name)) {
                if (mappedParameters.containsKey(name)) {
                    mappedParameters.get(name).addValue(value);
                } else {
                    mappedParameters.put(name, new Parameter(name, value));
                }
            }
        }
        return new ArrayList<KeyToMultiValue>(mappedParameters.values());
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
