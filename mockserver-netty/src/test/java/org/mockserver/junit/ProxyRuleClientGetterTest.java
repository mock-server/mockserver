package org.mockserver.junit;

import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * @author jamesdbloom
 */
public class ProxyRuleClientGetterTest {
    @Rule
    public ProxyRule proxyRule = new ProxyRule(this);

    @Test
    public void shouldSetTestProxyField() {
        assertThat(proxyRule.getClient(), is(not(nullValue())));
    }

}
