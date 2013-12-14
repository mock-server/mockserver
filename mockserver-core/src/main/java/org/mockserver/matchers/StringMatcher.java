package org.mockserver.matchers;

import com.google.common.base.Strings;
import org.mockserver.model.ModelObject;

import java.util.regex.PatternSyntaxException;

/**
 * @author jamesdbloom
 */
public class StringMatcher extends ModelObject implements Matcher<String> {

    private final String path;

    public StringMatcher(String path) {
        this.path = path;
    }

    public boolean matches(String path) {
        boolean result = false;

        if (Strings.isNullOrEmpty(this.path)) {
            result = true;
        } else if (path != null) {
            try {
                if (path.matches(this.path)) {
                    result = true;
                }
            } catch (PatternSyntaxException pse) {
                logger.error("Error while matching regex [" + this.path + "] for string [" + path + "] " + pse.getMessage());
            }
            try {
                if (this.path.matches(path)) {
                    result = true;
                }
            } catch (PatternSyntaxException pse) {
                logger.error("Error while matching regex [" + path + "] for string [" + this.path + "] " + pse.getMessage());
            }
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", path, this.path);
        }
        return result;
    }
}
