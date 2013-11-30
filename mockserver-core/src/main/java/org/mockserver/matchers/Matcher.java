package org.mockserver.matchers;

/**
 * @author jamesdbloom
 */
public interface Matcher<T> {

    public boolean matches(T t);
}
