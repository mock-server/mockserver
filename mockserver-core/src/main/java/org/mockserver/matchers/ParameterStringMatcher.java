package org.mockserver.matchers;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
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
    private final MultiValueMapMatcher matcher;

    public ParameterStringMatcher(Parameters parameters) {
        this.matcher = new MultiValueMapMatcher(((parameters != null ? parameters.toCaseInsensitiveRegexMultiMap() : new CaseInsensitiveRegexMultiMap())));
    }

    public boolean matches(String matched) {
        boolean result = false;

        if (matcher.matches(parseString(matched))) {
            result = true;
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
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
}
