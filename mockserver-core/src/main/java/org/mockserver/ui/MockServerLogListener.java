package org.mockserver.ui;

import org.mockserver.log.MockServerEventLog;

/**
 * @author jamesdbloom
 */
public interface MockServerLogListener {

    void updated(MockServerEventLog mockServerLog);

}
