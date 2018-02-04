package org.mockserver.junit;

import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author jamesdbloom
 */
public class MockServerRuleTest {

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    private MockServerClient mockServerClient;

    @Test
    public void shouldSetTestMockServeField() {
        assertThat(mockServerClient, is(not(nullValue())));
    }

    @Test
    public void shouldSetTestMockServerFieldWithSameValueFromGetter() {
        assertThat(mockServerClient, sameInstance(mockServerRule.getClient()));
    }

}
