package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.model.Cookie;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class CookieToJavaSerializer implements MultiValueToJavaSerializer<Cookie> {
    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, Cookie cookie) {
        return System.getProperty("line.separator") + Strings.padStart("", numberOfSpacesToIndent, ' ') + "new Cookie(\"" + cookie.getName() + "\"" + ", \"" + cookie.getValue() + "\"" + ")";
    }

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, List<Cookie> cookies) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < cookies.size(); i++) {
            output.append(serializeAsJava(numberOfSpacesToIndent, cookies.get(i)));
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
