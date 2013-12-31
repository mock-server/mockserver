package org.mockserver.server;

import org.mockserver.runner.AbstractRunner;

import javax.servlet.http.HttpServlet;

/**
 * @author jamesdbloom
 */
public class MockServerRunner extends AbstractRunner {

    protected HttpServlet getServlet() {
        return new MockServerServlet();
    }
}
