package org.mockserver.tomcat.server;

import org.mockserver.tomcat.runner.AbstractRunner;
import org.mockserver.server.MockServerServlet;

import javax.servlet.http.HttpServlet;

import static org.mockserver.configuration.SystemProperties.serverStopPort;

/**
 * @author jamesdbloom
 */
public class MockServerRunner extends AbstractRunner<MockServerRunner> {

    protected HttpServlet getServlet() {
        return new MockServerServlet();
    }

    @Override
    protected int stopPort(Integer port, Integer securePort) {
        return serverStopPort(port, securePort);
    }
}
