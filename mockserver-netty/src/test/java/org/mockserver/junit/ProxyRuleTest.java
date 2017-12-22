package org.mockserver.junit;

import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author jamesdbloom
 */
public class ProxyRuleTest {
    @Rule
    public ProxyRule proxyRule = new ProxyRule(this);

    private ProxyClient proxyClient;

    @Test
    public void shouldSetTestProxyField() {
        assertThat(proxyClient, is(not(nullValue())));
    }

    @Test
    public void shouldSetTestProxyFieldWithSameValueFromGetter() {
        assertThat(proxyClient, sameInstance(proxyRule.getClient()));
    }

}
