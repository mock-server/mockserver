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
public class MockServerRuleSubClassTest extends MockServerRuleSuperClass {

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    @Test
    public void shouldSetTestMockServeField() {
        assertThat(mockServerClient, is(not(nullValue())));
    }

}
