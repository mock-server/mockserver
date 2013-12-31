package org.mockserver.proxy;

import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.proxy.filters.LogFilter;
import org.mockserver.proxy.filters.ProxyRequestFilter;
import org.mockserver.proxy.filters.ProxyResponseFilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author jamesdbloom
 */
public class ProxyRunnerTest {

    @Test
    public void shouldReturnClassName() {
        ProxyRunner proxyRunner = new ProxyRunner();
        assertEquals(proxyRunner.getServlet().getClass(), ProxyServlet.class);
        assertSame(proxyRunner.getServlet(), proxyRunner.getServlet());
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

    @Test
    public void shouldAddFilters() {
        // given
        ProxyRunner proxyRunner = new ProxyRunner();
        proxyRunner.proxyServlet = mock(ProxyServlet.class);

        HttpRequest httpRequest = new HttpRequest();
        ProxyRequestFilter proxyRequestFilter = mock(ProxyRequestFilter.class);
        ProxyResponseFilter proxyResponseFilter = new LogFilter();

        // when
        proxyRunner.withFilter(httpRequest, proxyRequestFilter);
        proxyRunner.withFilter(httpRequest, proxyResponseFilter);

        // then
        verify(proxyRunner.proxyServlet).withFilter(same(httpRequest), same(proxyRequestFilter));
        verify(proxyRunner.proxyServlet).withFilter(same(httpRequest), same(proxyResponseFilter));

    }
}
