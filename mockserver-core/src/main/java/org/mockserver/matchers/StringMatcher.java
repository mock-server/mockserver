package org.mockserver.matchers;

import com.google.common.base.Strings;
import org.mockserver.model.ModelObject;

import java.util.regex.PatternSyntaxException;

/**
 * @author jamesdbloom
 */
public class StringMatcher extends ModelObject implements Matcher<String> {

    private final String matcher;

    public StringMatcher(String matcher) {
        this.matcher = matcher;
    }

    public boolean matches(String matched) {
        boolean result = false;

        if (Strings.isNullOrEmpty(this.matcher)) {
            result = true;
        } else if (matched != null) {
            try {
                if (matched.matches(this.matcher)) {
                    result = true;
                }
            } catch (PatternSyntaxException pse) {
                logger.error("Error while matching regex [" + this.matcher + "] for string [" + matched + "] " + pse.getMessage());
            }
            try {
                if (this.matcher.matches(matched)) {
                    result = true;
                }
            } catch (PatternSyntaxException pse) {
                logger.error("Error while matching regex [" + matched + "] for string [" + this.matcher + "] " + pse.getMessage());
            }
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        }
        return result;
    }
}
