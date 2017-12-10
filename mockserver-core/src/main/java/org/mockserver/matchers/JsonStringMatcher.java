package org.mockserver.matchers;

import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;

import static org.skyscreamer.jsonassert.JSONCompare.compareJSON;

/**
 * @author jamesdbloom
 */
public class JsonStringMatcher extends BodyMatcher<String> {
    private final String matcher;
    private final MatchType matchType;

    public JsonStringMatcher(String matcher, MatchType matchType) {
        this.matcher = matcher;
        this.matchType = matchType;
    }

    public boolean matches(String matched) {
        boolean result = false;

        JSONCompareResult jsonCompareResult;
        try {
            JSONCompareMode jsonCompareMode = JSONCompareMode.LENIENT;
            if (matchType == MatchType.STRICT) {
                jsonCompareMode = JSONCompareMode.STRICT;
            }
            jsonCompareResult = compareJSON(matcher, matched, jsonCompareMode);

            if (jsonCompareResult.passed()) {
                result = true;
            }

            if (!result) {
                logger.trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", matched, this.matcher, jsonCompareResult.getMessage());
            }
        } catch (Exception e) {
            logger.trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", matched, this.matcher, e.getMessage());
        }

        return reverseResultIfNot(result);
    }
}
