package org.mockserver.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.model.Cookie;

import java.util.Arrays;
import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class CookieToJavaSerializer implements MultiValueToJavaSerializer<Cookie> {
    @Override
    public String serialize(int numberOfSpacesToIndent, Cookie cookie) {
        return NEW_LINE + Strings.padStart("", numberOfSpacesToIndent * INDENT_SIZE, ' ') + "new Cookie(" +
            NottableStringToJavaSerializer.serializeNottableString(cookie.getName()) + ", " +
            NottableStringToJavaSerializer.serializeNottableString(cookie.getValue()) + ")";
    }

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, List<Cookie> cookies) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < cookies.size(); i++) {
            output.append(serialize(numberOfSpacesToIndent, cookies.get(i)));
            if (i < (cookies.size() - 1)) {
                output.append(",");
            }
        }
        return output.toString();
    }

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, Cookie... object) {
        return serializeAsJava(numberOfSpacesToIndent, Arrays.asList(object));
    }
}
