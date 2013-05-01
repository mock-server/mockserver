package org.mockserver.matchers;

import com.google.common.base.Strings;
import org.mockserver.model.ModelObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        } else if (path != null && path.matches(this.path)) {
            result = true;
        } else {
            logger.trace("Failed to match {} with {}", path, this.path);
        }

        return result;
    }
}
