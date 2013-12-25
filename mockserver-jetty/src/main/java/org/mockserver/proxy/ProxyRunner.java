package org.mockserver.proxy;

import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.mockserver.runner.AbstractRunner;

/**
 * @author jamesdbloom
 */
public class ProxyRunner extends AbstractRunner {
    protected String getServletName() {
        return ProxyServlet.class.getName();
    }

    @Override
    protected void extendHTTPConfig(HttpConfiguration https_config) {
        https_config.addCustomizer(new ForwardedRequestCustomizer());
    }
}
