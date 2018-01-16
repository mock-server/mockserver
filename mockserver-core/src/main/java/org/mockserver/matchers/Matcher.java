package org.mockserver.matchers;

import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public interface Matcher<T> {

    boolean matches(HttpRequest context, T t);
}
