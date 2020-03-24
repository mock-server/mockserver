package org.mockserver.matchers;

/**
 * @author jamesdbloom
 */
public interface Matcher<T> {

    boolean matches(MatchDifference context, T t);

    boolean isBlank();

}
