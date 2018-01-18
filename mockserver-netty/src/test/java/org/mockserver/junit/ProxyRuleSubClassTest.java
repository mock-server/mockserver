package org.mockserver.junit;

import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * @author jamesdbloom
 */
public class ProxyRuleSubClassTest extends ProxyRuleSuperClass {
    @Rule
    public ProxyRule proxyRule = new ProxyRule(this);

    @Test
    public void shouldSetTestProxyField() {
        assertThat(proxyClient, is(not(nullValue())));
    }

}
