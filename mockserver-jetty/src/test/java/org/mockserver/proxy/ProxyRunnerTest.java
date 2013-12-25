package org.mockserver.proxy;

import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author jamesdbloom
 */
public class ProxyRunnerTest {

    @Test
    public void shouldReturnClassName() {
        assertEquals(new ProxyRunner().getServletName(), ProxyServlet.class.getName());
    }

    @Test
    public void shouldAddForwardedRequestCustomizer() {
        // given
        HttpConfiguration https_config = mock(HttpConfiguration.class);

        // when
        new ProxyRunner().extendHTTPConfig(https_config);

        // then
        verify(https_config).addCustomizer(any(ForwardedRequestCustomizer.class));
    }
}
