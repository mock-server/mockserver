package org.mockserver.tomcat.proxy;

import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.proxy.ProxyServlet;
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
