package org.mockserver.matchers;

import org.skyscreamer.jsonassert.JSONCompareResult;

import static org.skyscreamer.jsonassert.JSONCompare.compareJSON;

/**
 * @author jamesdbloom
 */
public class JsonStringMatcher extends BodyMatcher<String> implements Matcher<String> {
    private final String matcher;
    private final JsonBodyMatchType jsonBodyMatchType;

    public JsonStringMatcher(String matcher, JsonBodyMatchType jsonBodyMatchType) {
        this.matcher = matcher;
        this.jsonBodyMatchType = jsonBodyMatchType;
    }

    public boolean matches(String matched) {
        boolean result = false;

        JSONCompareResult jsonCompareResult;
        try {
            jsonCompareResult = compareJSON(matcher, matched, jsonBodyMatchType.getNonExtensible());

            if (jsonCompareResult.passed()) {
                result = true;
            }

            if (!result) {
                logger.trace("Failed to perform JSON match [{}] with [{}] because {}", matched, this.matcher, jsonCompareResult.getMessage());
            }
        } catch (Exception e) {
            logger.trace("Failed to perform JSON match [{}] with [{}] because {}", matched, this.matcher, e.getMessage());
        }

        return result;
    }
}
