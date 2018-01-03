package org.mockserver.mockserver.ui;

import org.mockserver.filters.MockServerLog;

/**
 * @author jamesdbloom
 */
public interface MockServerLogListener {

    void updated(MockServerLog mockServerLog);
}
