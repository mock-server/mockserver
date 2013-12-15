package org.mockserver.server;

import org.mockserver.runner.AbstractRunner;

/**
 * @author jamesdbloom
 */
public class MockServerRunner extends AbstractRunner {
    protected String getServletName() {
        return MockServerServlet.class.getName();
    }
}
