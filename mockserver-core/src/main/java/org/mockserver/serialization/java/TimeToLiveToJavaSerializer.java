package org.mockserver.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.matchers.TimeToLive;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class TimeToLiveToJavaSerializer implements ToJavaSerializer<TimeToLive> {

    @Override
    public String serialize(int numberOfSpacesToIndent, TimeToLive timeToLive) {
        StringBuffer output = new StringBuffer();
        if (timeToLive != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output);
            if (timeToLive.isUnlimited()) {
                output.append("TimeToLive.unlimited()");
            } else {
                output.append("TimeToLive.exactly(TimeUnit.").append(timeToLive.getTimeUnit().name()).append(", ").append(timeToLive.getTimeToLive()).append("L)");
            }
        }

        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}
