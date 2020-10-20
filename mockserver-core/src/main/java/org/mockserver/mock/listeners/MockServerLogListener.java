package org.mockserver.mock.listeners;

import org.mockserver.log.MockServerEventLog;

/**
 * @author jamesdbloom
 */
public interface MockServerLogListener {

    void updated(MockServerEventLog mockServerLog);

}
