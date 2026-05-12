package org.mockserver.matchers;

import java.util.List;
import java.util.Map;

import static org.mockserver.character.Character.NEW_LINE;

public class MatchDifferenceFormatter {

    private static final int MAX_DIFF_LINE_LENGTH = 500;

    public static String formatDifferences(Map<MatchDifference.Field, List<String>> differences) {
        if (differences == null || differences.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE).append(NEW_LINE).append("closest match diff:").append(NEW_LINE);
        for (MatchDifference.Field field : MatchDifference.Field.values()) {
            List<String> fieldDiffs = differences.get(field);
            if (fieldDiffs != null && !fieldDiffs.isEmpty()) {
                builder.append("  ").append(field.getName()).append(":").append(NEW_LINE);
                for (String diff : fieldDiffs) {
                    String truncated = truncateDiffLine(diff);
                    builder.append("    ").append(truncated).append(NEW_LINE);
                }
            }
        }
        return builder.toString();
    }

    static String truncateDiffLine(String diff) {
        if (diff == null) {
            return "";
        }
        String singleLine = diff.replace("\n", " ").replace("\r", "");
        if (singleLine.length() > MAX_DIFF_LINE_LENGTH) {
            return singleLine.substring(0, MAX_DIFF_LINE_LENGTH) + "...[truncated]";
        }
        return singleLine;
    }

}
