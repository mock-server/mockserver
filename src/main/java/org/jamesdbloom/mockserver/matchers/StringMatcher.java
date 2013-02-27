package org.jamesdbloom.mockserver.matchers;

import com.google.common.base.Strings;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.model.ModelObject;

import static org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

/**
 * @author jamesdbloom
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class StringMatcher extends ModelObject implements Matcher<String> {
    private final String path;

    @JsonCreator
    public StringMatcher(@JsonProperty("path") String path) {
        this.path = path;
    }

    public boolean matches(String path) {
        boolean result = false;

        if (Strings.isNullOrEmpty(this.path)) {
            result = true;
        } else if (path != null && path.matches(this.path)) {
            result = true;
        }

        return result;
    }
}
