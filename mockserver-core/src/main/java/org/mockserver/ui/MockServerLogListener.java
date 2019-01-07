package org.mockserver.ui;

import org.mockserver.filters.MockServerEventLog;

/**
 * @author jamesdbloom
 */
public interface MockServerLogListener {

    void updated(MockServerEventLog mockServerLog);

}
