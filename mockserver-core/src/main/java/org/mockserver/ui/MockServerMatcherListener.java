package org.mockserver.ui;

import org.mockserver.mock.RequestMatchers;

/**
 * @author jamesdbloom
 */
public interface MockServerMatcherListener {

    void updated(RequestMatchers requestMatchers, MockServerMatcherNotifier.Cause cause);

}
