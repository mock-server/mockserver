package org.mockserver.matchers;

/**
 * Base interface for all matchers in MockServer.
 * <p>
 * <strong>Null/Blank Matcher Behavior:</strong> When a matcher is null or blank (as determined by
 * {@link #isBlank()}), it matches <em>all</em> values. This "match-all" behavior is intentional
 * and improves the user experience when creating expectations. Matchers act as filters: if a
 * filter is not specified (null/blank), everything passes through.
 * </p>
 * <p>
 * For example, if an expectation does not specify a path matcher, it will match requests with
 * any path. This allows users to create expectations that match broadly without explicitly
 * specifying wildcards or "match anything" patterns for every field.
 * </p>
 *
 * @author jamesdbloom
 */
public interface Matcher<T> {

    boolean matches(MatchDifference context, T t);

    boolean isBlank();

}
