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
public class MockServerRuleClientGetterTest {

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    @Test
    public void shouldSetTestMockServeField() {
        assertThat(mockServerRule.getClient(), is(not(nullValue())));
    }

}
