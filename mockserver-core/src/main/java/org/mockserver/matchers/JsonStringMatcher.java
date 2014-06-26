package org.mockserver.matchers;

import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.skyscreamer.jsonassert.JSONCompare.compareJSON;

/**
 * @author jamesdbloom
 */
public class JsonStringMatcher extends BodyMatcher<String> implements Matcher<String> {
    private static Logger logger = LoggerFactory.getLogger(JsonStringMatcher.class);
    private final String matcher;

    public JsonStringMatcher(String matcher) {
        this.matcher = matcher;
    }

    public boolean matches(String matched) {
        boolean result = false;

        JSONCompareResult jsonCompareResult = null;
        try {
            jsonCompareResult = compareJSON(matcher, matched, JSONCompareMode.LENIENT);

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

    @Override
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger"};
    }
}
