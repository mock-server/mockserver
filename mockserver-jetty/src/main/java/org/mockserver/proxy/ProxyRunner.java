package org.mockserver.proxy;

import org.mockserver.runner.AbstractRunner;

/**
 * @author jamesdbloom
 */
public class ProxyRunner extends AbstractRunner {
    protected String getServletName() {
        return ProxyServlet.class.getName();
    }
}
