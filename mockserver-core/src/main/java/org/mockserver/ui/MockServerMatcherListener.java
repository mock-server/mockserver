package org.mockserver.ui;

import org.mockserver.mock.MockServerMatcher;

/**
 * @author jamesdbloom
 */
public interface MockServerMatcherListener {

    void updated(MockServerMatcher mockServerMatcher, MockServerMatcherNotifier.Cause cause);

}
