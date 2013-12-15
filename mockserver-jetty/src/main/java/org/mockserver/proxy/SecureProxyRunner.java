package org.mockserver.proxy;

import org.eclipse.jetty.server.Server;
import org.mockserver.runner.AbstractRunner;

/**
 * @author jamesdbloom
 */
public class SecureProxyRunner extends AbstractRunner {
    protected String getServletName() {
        return ProxyServlet.class.getName();
    }

    protected void addCertificates(Server server) {

    }
}
