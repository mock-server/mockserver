package org.mockserver.time;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * Strict date parsing shared by the {@code dates} template helper and the date-time request matchers:
 * an impossible date such as 30 February fails instead of being adjusted.
 */
public final class StrictDateFormats {

    /**
     * RFC 1123 with strict resolution; the JDK's own formatter is SMART, which turns 30 Feb into 28 Feb.
     */
    public static final DateTimeFormatter RFC_1123_STRICT = DateTimeFormatter.RFC_1123_DATE_TIME.withResolverStyle(ResolverStyle.STRICT);

    /**
     * Epoch values with a magnitude below this are seconds (up to the year 5138), at or above it millis.
     */
    public static final long EPOCH_MILLIS_THRESHOLD = 100_000_000_000L;

    private StrictDateFormats() {
    }

    /**
     * A strict parser for a {@link DateTimeFormatter} pattern with English month and day names.
     *
     * @throws IllegalArgumentException when the pattern is invalid
     */
    public static DateTimeFormatter strictParser(String pattern) {
        return DateTimeFormatter.ofPattern(strictPattern(pattern), Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    }

    /**
     * Strict resolution needs an era for {@code y} (year of era), so an unquoted {@code y} is rewritten as
     * {@code u} (proleptic year), which is the same for years after 0.
     */
    public static String strictPattern(String pattern) {
        if (pattern.indexOf('y') < 0) {
            return pattern;
        }
        StringBuilder result = new StringBuilder(pattern.length());
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            }
            result.append(!quoted && c == 'y' ? 'u' : c);
        }
        return result.toString();
    }
}
